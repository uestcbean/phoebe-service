package com.phoebe.controller;

import com.phoebe.context.RequestUserHolder;
import com.phoebe.entity.Note;
import com.phoebe.entity.UserKnowledgeBase;
import com.phoebe.mapper.NoteMapper;
import com.phoebe.scheduler.NotesSyncScheduler;
import com.phoebe.service.BailianKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

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
    private final NoteMapper noteMapper;

    public KnowledgeBaseController(
            BailianKnowledgeService bailianKnowledgeService,
            NotesSyncScheduler notesSyncScheduler,
            NoteMapper noteMapper) {
        this.bailianKnowledgeService = bailianKnowledgeService;
        this.notesSyncScheduler = notesSyncScheduler;
        this.noteMapper = noteMapper;
    }

    /**
     * Get knowledge base info for current user.
     * Creates one if it doesn't exist.
     */
    @GetMapping
    public ResponseEntity<UserKnowledgeBase> getCurrentUserKnowledgeBase(ServerWebExchange exchange) {
        Long userId = RequestUserHolder.getUserId(exchange);
        log.info("Getting knowledge base for user: {}", userId);
        try {
            UserKnowledgeBase kb = bailianKnowledgeService.getOrCreateKnowledgeBase(userId);
            return ResponseEntity.ok(kb);
        } catch (Exception e) {
            log.error("Error getting knowledge base for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manually trigger sync for current user's notes.
     * This will sync all un-synced notes to the knowledge base.
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncCurrentUserNotes(ServerWebExchange exchange) {
        Long userId = RequestUserHolder.getUserId(exchange);
        log.info("Manual sync triggered for user: {}", userId);
        try {
            int count = notesSyncScheduler.syncNotesForUser(userId);
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("syncedCount", count);
            result.put("message", "Sync completed successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error syncing notes for user {}: {}", userId, e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Force re-sync all notes for current user.
     * Use with caution as this may create duplicate documents.
     */
    @PostMapping("/force-sync")
    public ResponseEntity<Map<String, Object>> forceSyncCurrentUserNotes(ServerWebExchange exchange) {
        Long userId = RequestUserHolder.getUserId(exchange);
        log.info("Force sync triggered for user: {}", userId);
        try {
            int count = notesSyncScheduler.forceSyncAllNotesForUser(userId);
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("syncedCount", count);
            result.put("message", "Force sync completed successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error force syncing notes for user {}: {}", userId, e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Check sync status for a specific note.
     */
    @GetMapping("/note/{noteId}/sync-status")
    public ResponseEntity<Map<String, Object>> getNoteSyncStatus(@PathVariable Long noteId) {
        boolean synced = bailianKnowledgeService.isNoteSynced(noteId);
        return ResponseEntity.ok(Map.of(
                "noteId", noteId,
                "synced", synced
        ));
    }

    /**
     * Update a note in the knowledge base.
     * This will delete the old document and add the new updated content.
     * Only works for notes that have been synced before.
     */
    @PostMapping("/note/{noteId}/update")
    public ResponseEntity<Map<String, Object>> updateNoteInKnowledgeBase(@PathVariable Long noteId) {
        log.info("Update note {} in knowledge base requested", noteId);
        
        try {
            // Get the note from database
            Note note = noteMapper.findById(noteId);
            if (note == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if note is active
            if (note.getStatus() != Note.STATUS_ACTIVE) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Note is not active",
                        "noteId", noteId
                ));
            }
            
            // Update the note in knowledge base
            var history = bailianKnowledgeService.updateNoteInKnowledgeBase(note);
            
            Map<String, Object> result = new HashMap<>();
            result.put("noteId", noteId);
            result.put("success", history != null && "SUCCESS".equals(history.getSyncStatus()));
            result.put("documentId", history != null ? history.getDocumentId() : null);
            result.put("message", history != null && "SUCCESS".equals(history.getSyncStatus()) 
                    ? "Note updated successfully in knowledge base" 
                    : "Failed to update note: " + (history != null ? history.getErrorMessage() : "unknown error"));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error updating note {} in knowledge base: {}", noteId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage(),
                    "noteId", noteId
            ));
        }
    }

    /**
     * Batch update notes for current user in knowledge base.
     * This will re-sync all previously synced notes with their current content.
     */
    @PostMapping("/update-synced")
    public ResponseEntity<Map<String, Object>> updateSyncedNotesForCurrentUser(ServerWebExchange exchange) {
        Long userId = RequestUserHolder.getUserId(exchange);
        log.info("Batch update synced notes for user {} requested", userId);
        
        try {
            List<Note> activeNotes = noteMapper.findActiveByUserId(userId);
            if (activeNotes == null || activeNotes.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "userId", userId,
                        "updatedCount", 0,
                        "message", "No active notes found"
                ));
            }
            
            int updatedCount = 0;
            int failedCount = 0;
            
            for (Note note : activeNotes) {
                // Only update notes that were previously synced
                if (bailianKnowledgeService.isNoteSynced(note.getId())) {
                    try {
                        var history = bailianKnowledgeService.updateNoteInKnowledgeBase(note);
                        if (history != null && "SUCCESS".equals(history.getSyncStatus())) {
                            updatedCount++;
                        } else {
                            failedCount++;
                        }
                        // Add delay to avoid rate limiting
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Error updating note {}: {}", note.getId(), e.getMessage());
                        failedCount++;
                    }
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("updatedCount", updatedCount);
            result.put("failedCount", failedCount);
            result.put("message", "Batch update completed");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error batch updating notes for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage(),
                    "userId", userId
            ));
        }
    }

    /**
     * Trigger global sync (all users).
     * This is the same as the scheduled task but triggered manually.
     */
    @PostMapping("/sync-all")
    public ResponseEntity<Map<String, String>> triggerGlobalSync() {
        log.info("Manual global sync triggered");
        // Run in a separate thread to avoid blocking
        new Thread(() -> notesSyncScheduler.syncNotesToKnowledgeBase()).start();
        return ResponseEntity.ok(Map.of(
                "message", "Global sync started in background"
        ));
    }

    /**
     * Debug endpoint: Get notes status for current user.
     * Shows all notes and their sync status.
     */
    @GetMapping("/notes-status")
    public ResponseEntity<Map<String, Object>> getCurrentUserNotesStatus(ServerWebExchange exchange) {
        Long userId = RequestUserHolder.getUserId(exchange);
        log.info("Getting notes status for user: {}", userId);
        
        try {
            List<Note> activeNotes = noteMapper.findByUserIdAndStatus(userId, Note.STATUS_ACTIVE);
            List<Note> allNotes = noteMapper.findAll().stream()
                    .filter(note -> userId.equals(note.getUserId()))
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("activeNotesCount", activeNotes != null ? activeNotes.size() : 0);
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
        } catch (Exception e) {
            log.error("Error getting notes status for user {}: {}", userId, e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }
}
