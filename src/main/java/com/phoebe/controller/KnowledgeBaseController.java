package com.phoebe.controller;

import com.phoebe.entity.Note;
import com.phoebe.entity.UserKnowledgeBase;
import com.phoebe.repository.NoteRepository;
import com.phoebe.scheduler.NotesSyncScheduler;
import com.phoebe.service.BailianKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for knowledge base management operations.
 * Provides endpoints for manual sync and status checking.
 */
@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final BailianKnowledgeService bailianKnowledgeService;
    private final NotesSyncScheduler notesSyncScheduler;
    private final NoteRepository noteRepository;

    public KnowledgeBaseController(
            BailianKnowledgeService bailianKnowledgeService,
            NotesSyncScheduler notesSyncScheduler,
            NoteRepository noteRepository) {
        this.bailianKnowledgeService = bailianKnowledgeService;
        this.notesSyncScheduler = notesSyncScheduler;
        this.noteRepository = noteRepository;
    }

    /**
     * Get knowledge base info for a user.
     * Creates one if it doesn't exist.
     */
    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<UserKnowledgeBase>> getUserKnowledgeBase(@PathVariable String userId) {
        log.info("Getting knowledge base for user: {}", userId);
        return bailianKnowledgeService.getOrCreateKnowledgeBase(userId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error getting knowledge base for user {}: {}", userId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * Manually trigger sync for a user's notes.
     * This will sync all un-synced notes to the knowledge base.
     */
    @PostMapping("/user/{userId}/sync")
    public Mono<ResponseEntity<Map<String, Object>>> syncUserNotes(@PathVariable String userId) {
        log.info("Manual sync triggered for user: {}", userId);
        return notesSyncScheduler.syncNotesForUser(userId)
                .map(count -> {
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("userId", userId);
                    result.put("syncedCount", count);
                    result.put("message", "Sync completed successfully");
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(e -> {
                    log.error("Error syncing notes for user {}: {}", userId, e.getMessage());
                    Map<String, Object> errorResult = new java.util.HashMap<>();
                    errorResult.put("error", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(errorResult));
                });
    }

    /**
     * Force re-sync all notes for a user.
     * Use with caution as this may create duplicate documents.
     */
    @PostMapping("/user/{userId}/force-sync")
    public Mono<ResponseEntity<Map<String, Object>>> forceSyncUserNotes(@PathVariable String userId) {
        log.info("Force sync triggered for user: {}", userId);
        return notesSyncScheduler.forceSyncAllNotesForUser(userId)
                .map(count -> {
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("userId", userId);
                    result.put("syncedCount", count);
                    result.put("message", "Force sync completed successfully");
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(e -> {
                    log.error("Error force syncing notes for user {}: {}", userId, e.getMessage());
                    Map<String, Object> errorResult = new java.util.HashMap<>();
                    errorResult.put("error", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(errorResult));
                });
    }

    /**
     * Check sync status for a specific note.
     */
    @GetMapping("/note/{noteId}/sync-status")
    public Mono<ResponseEntity<Map<String, Object>>> getNoteSyncStatus(@PathVariable String noteId) {
        return bailianKnowledgeService.isNoteSynced(noteId)
                .map(synced -> ResponseEntity.ok(Map.of(
                        "noteId", noteId,
                        "synced", synced
                )));
    }

    /**
     * Trigger global sync (all users).
     * This is the same as the scheduled task but triggered manually.
     */
    @PostMapping("/sync-all")
    public ResponseEntity<Map<String, String>> triggerGlobalSync() {
        log.info("Manual global sync triggered");
        notesSyncScheduler.syncNotesToKnowledgeBase();
        return ResponseEntity.ok(Map.of(
                "message", "Global sync started in background"
        ));
    }

    /**
     * Debug endpoint: Get notes status for a user.
     * Shows all notes and their sync status.
     */
    @GetMapping("/user/{userId}/notes-status")
    public Mono<ResponseEntity<Map<String, Object>>> getUserNotesStatus(@PathVariable String userId) {
        log.info("Getting notes status for user: {}", userId);
        
        return noteRepository.findByUserIdAndStatus(userId, Note.STATUS_ACTIVE)
                .collectList()
                .flatMap(activeNotes -> {
                    // Also get all notes regardless of status
                    return noteRepository.findAll()
                            .filter(note -> userId.equals(note.getUserId()))
                            .collectList()
                            .map(allNotes -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("userId", userId);
                                result.put("activeNotesCount", activeNotes.size());
                                result.put("totalNotesCount", allNotes.size());
                                
                                // Show note details
                                List<Map<String, Object>> noteDetails = allNotes.stream()
                                        .map(note -> {
                                            Map<String, Object> detail = new HashMap<>();
                                            detail.put("id", note.getId());
                                            detail.put("title", note.getTitle());
                                            detail.put("status", note.getStatus());
                                            detail.put("createdAt", note.getCreatedAt());
                                            return detail;
                                        })
                                        .collect(Collectors.toList());
                                result.put("notes", noteDetails);
                                
                                return ResponseEntity.ok(result);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error getting notes status for user {}: {}", userId, e.getMessage());
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("error", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(errorResult));
                });
    }
}

