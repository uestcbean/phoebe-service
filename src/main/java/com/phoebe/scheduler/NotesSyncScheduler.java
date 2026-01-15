package com.phoebe.scheduler;

import com.phoebe.config.BailianConfig;
import com.phoebe.entity.Note;
import com.phoebe.repository.NoteRepository;
import com.phoebe.service.BailianKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled task to sync notes to Bailian knowledge base.
 * 
 * This scheduler runs daily (configurable via bailian.sync-cron) to:
 * 1. Find all active notes that haven't been synced
 * 2. Upload each note to the user's knowledge base in Bailian
 * 3. Track sync status in note_sync_history table
 */
@Component
public class NotesSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotesSyncScheduler.class);

    private final NoteRepository noteRepository;
    private final BailianKnowledgeService bailianKnowledgeService;
    private final BailianConfig bailianConfig;

    public NotesSyncScheduler(
            NoteRepository noteRepository,
            BailianKnowledgeService bailianKnowledgeService,
            BailianConfig bailianConfig) {
        this.noteRepository = noteRepository;
        this.bailianKnowledgeService = bailianKnowledgeService;
        this.bailianConfig = bailianConfig;
    }

    /**
     * Scheduled task to sync notes to knowledge base.
     * Default schedule: daily at 2 AM (configurable via bailian.sync-cron)
     */
    @Scheduled(cron = "${bailian.sync-cron:0 0 2 * * ?}")
    public void syncNotesToKnowledgeBase() {
        if (!bailianConfig.isSyncEnabled()) {
            log.info("Knowledge base sync is disabled, skipping...");
            return;
        }

        log.info("Starting scheduled notes sync to knowledge base...");
        
        OffsetDateTime startTime = OffsetDateTime.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);

        // Find all active notes and sync those that haven't been synced
        noteRepository.findAll()
                // Get all active notes
                .filter(note -> note.getStatus() != null && note.getStatus() == Note.STATUS_ACTIVE)
                // Filter out already synced notes
                .filterWhen(note -> bailianKnowledgeService.isNoteSynced(note.getId())
                        .map(synced -> {
                            if (synced) {
                                skipCount.incrementAndGet();
                            }
                            return !synced;
                        }))
                // Add delay between requests to avoid rate limiting
                .delayElements(Duration.ofMillis(500))
                // Sync each note
                .flatMap(note -> syncNote(note)
                        .doOnSuccess(success -> {
                            if (success) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                        })
                        .onErrorResume(e -> {
                            failCount.incrementAndGet();
                            return Mono.just(false);
                        }))
                .collectList()
                .doOnSuccess(results -> {
                    Duration duration = Duration.between(startTime, OffsetDateTime.now());
                    log.info("Completed notes sync to knowledge base. " +
                                    "Success: {}, Failed: {}, Skipped: {}, Duration: {}s",
                            successCount.get(), failCount.get(), skipCount.get(), 
                            duration.getSeconds());
                })
                .doOnError(e -> log.error("Error during notes sync: {}", e.getMessage()))
                .subscribe();
    }

    /**
     * Sync a single note to knowledge base.
     * Returns true if sync was successful, false otherwise.
     */
    private Mono<Boolean> syncNote(Note note) {
        log.debug("Syncing note {} for user {}", note.getId(), note.getUserId());
        
        return bailianKnowledgeService.addNoteToKnowledgeBase(note)
                .map(history -> "SUCCESS".equals(history.getSyncStatus()))
                .onErrorResume(e -> {
                    log.error("Failed to sync note {}: {}", note.getId(), e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Manually trigger sync for a specific user's notes.
     * Can be called via API for on-demand sync.
     */
    public Mono<Integer> syncNotesForUser(String userId) {
        if (!bailianConfig.isSyncEnabled()) {
            log.warn("Knowledge base sync is disabled");
            return Mono.just(0);
        }

        log.info("Starting manual notes sync for user: {}", userId);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        return noteRepository.findActiveByUserId(userId)
                .doOnNext(note -> {
                    totalCount.incrementAndGet();
                    log.info("Found note to sync: id={}, title={}, status={}", 
                            note.getId(), note.getTitle(), note.getStatus());
                })
                .filterWhen(note -> bailianKnowledgeService.isNoteSynced(note.getId())
                        .doOnNext(synced -> {
                            if (synced) {
                                skippedCount.incrementAndGet();
                                log.info("Note {} already synced, skipping", note.getId());
                            }
                        })
                        .map(synced -> !synced))
                .delayElements(Duration.ofMillis(300))
                .flatMap(note -> syncNote(note)
                        .doOnSuccess(success -> {
                            if (success) {
                                successCount.incrementAndGet();
                            }
                        }))
                .collectList()
                .map(results -> {
                    log.info("Completed manual sync for user {}: total={}, synced={}, skipped={}", 
                            userId, totalCount.get(), successCount.get(), skippedCount.get());
                    return successCount.get();
                });
    }

    /**
     * Force re-sync all notes for a user (including previously synced ones).
     * Use with caution as this may create duplicate documents.
     */
    public Mono<Integer> forceSyncAllNotesForUser(String userId) {
        if (!bailianConfig.isSyncEnabled()) {
            log.warn("Knowledge base sync is disabled");
            return Mono.just(0);
        }

        log.info("Starting force sync for all notes of user: {}", userId);
        AtomicInteger successCount = new AtomicInteger(0);

        return noteRepository.findActiveByUserId(userId)
                .delayElements(Duration.ofMillis(300))
                .flatMap(note -> syncNote(note)
                        .doOnSuccess(success -> {
                            if (success) {
                                successCount.incrementAndGet();
                            }
                        }))
                .collectList()
                .map(results -> {
                    log.info("Completed force sync for user {}, synced {} notes", 
                            userId, successCount.get());
                    return successCount.get();
                });
    }
}

