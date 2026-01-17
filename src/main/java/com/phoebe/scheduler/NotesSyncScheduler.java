package com.phoebe.scheduler;

import com.phoebe.config.BailianConfig;
import com.phoebe.entity.Note;
import com.phoebe.entity.NoteSyncHistory;
import com.phoebe.mapper.NoteMapper;
import com.phoebe.service.BailianKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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

    private final NoteMapper noteMapper;
    private final BailianKnowledgeService bailianKnowledgeService;
    private final BailianConfig bailianConfig;

    public NotesSyncScheduler(
            NoteMapper noteMapper,
            BailianKnowledgeService bailianKnowledgeService,
            BailianConfig bailianConfig) {
        this.noteMapper = noteMapper;
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
        
        LocalDateTime startTime = LocalDateTime.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);

        // Find all active notes
        List<Note> notes = noteMapper.findAll();

        if (notes == null || notes.isEmpty()) {
            log.info("No active notes found to sync");
            return;
        }

        // Filter active notes
        List<Note> activeNotes = notes.stream()
                .filter(note -> note.getStatus() != null && note.getStatus() == Note.STATUS_ACTIVE)
                .toList();

        for (Note note : activeNotes) {
            try {
                // Check if already synced
                boolean synced = bailianKnowledgeService.isNoteSynced(note.getId());
                if (synced) {
                    skipCount.incrementAndGet();
                    continue;
                }

                // Add delay between requests to avoid rate limiting
                Thread.sleep(500);

                // Sync the note
                boolean success = syncNote(note);
                if (success) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sync interrupted");
                break;
            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("Error syncing note {}: {}", note.getId(), e.getMessage());
            }
        }

        Duration duration = Duration.between(startTime, LocalDateTime.now());
        log.info("Completed notes sync to knowledge base. " +
                        "Success: {}, Failed: {}, Skipped: {}, Duration: {}s",
                successCount.get(), failCount.get(), skipCount.get(), 
                duration.getSeconds());
    }

    /**
     * Sync a single note to knowledge base.
     * Returns true if sync was successful, false otherwise.
     */
    private boolean syncNote(Note note) {
        log.debug("Syncing note {} for user {}", note.getId(), note.getUserId());
        
        try {
            NoteSyncHistory history = bailianKnowledgeService.addNoteToKnowledgeBase(note);
            return history != null && NoteSyncHistory.STATUS_SUCCESS.equals(history.getSyncStatus());
        } catch (Exception e) {
            log.error("Failed to sync note {}: {}", note.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Manually trigger sync for a specific user's notes.
     * Can be called via API for on-demand sync.
     */
    public int syncNotesForUser(Long userId) {
        if (!bailianConfig.isSyncEnabled()) {
            log.warn("Knowledge base sync is disabled");
            return 0;
        }

        log.info("Starting manual notes sync for user: {}", userId);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        List<Note> notes = noteMapper.findActiveByUserId(userId);

        if (notes == null || notes.isEmpty()) {
            log.info("No active notes found for user: {}", userId);
            return 0;
        }

        for (Note note : notes) {
            totalCount.incrementAndGet();
            log.info("Found note to sync: id={}, title={}, status={}", 
                    note.getId(), note.getTitle(), note.getStatus());

            try {
                // Check if already synced
                boolean synced = bailianKnowledgeService.isNoteSynced(note.getId());
                if (synced) {
                    skippedCount.incrementAndGet();
                    log.info("Note {} already synced, skipping", note.getId());
                    continue;
                }

                // Add delay between requests
                Thread.sleep(300);

                // Sync the note
                boolean success = syncNote(note);
                if (success) {
                    successCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sync interrupted");
                break;
            } catch (Exception e) {
                log.error("Error syncing note {}: {}", note.getId(), e.getMessage());
            }
        }

        log.info("Completed manual sync for user {}: total={}, synced={}, skipped={}", 
                userId, totalCount.get(), successCount.get(), skippedCount.get());
        return successCount.get();
    }

    /**
     * Force re-sync all notes for a user (including previously synced ones).
     * Use with caution as this may create duplicate documents.
     */
    public int forceSyncAllNotesForUser(Long userId) {
        if (!bailianConfig.isSyncEnabled()) {
            log.warn("Knowledge base sync is disabled");
            return 0;
        }

        log.info("Starting force sync for all notes of user: {}", userId);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Note> notes = noteMapper.findActiveByUserId(userId);

        if (notes == null || notes.isEmpty()) {
            log.info("No active notes found for user: {}", userId);
            return 0;
        }

        for (Note note : notes) {
            try {
                // Add delay between requests
                Thread.sleep(300);

                // Sync the note
                boolean success = syncNote(note);
                if (success) {
                    successCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sync interrupted");
                break;
            } catch (Exception e) {
                log.error("Error syncing note {}: {}", note.getId(), e.getMessage());
            }
        }

        log.info("Completed force sync for user {}, synced {} notes", 
                userId, successCount.get());
        return successCount.get();
    }
}
