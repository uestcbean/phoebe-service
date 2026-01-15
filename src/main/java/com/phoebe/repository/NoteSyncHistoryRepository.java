package com.phoebe.repository;

import com.phoebe.entity.NoteSyncHistory;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface NoteSyncHistoryRepository extends ReactiveCrudRepository<NoteSyncHistory, String> {

    /**
     * Find sync history by note ID
     */
    Flux<NoteSyncHistory> findByNoteId(String noteId);

    /**
     * Find the latest sync record for a note
     */
    Mono<NoteSyncHistory> findFirstByNoteIdOrderBySyncedAtDesc(String noteId);

    /**
     * Find all sync records for a user
     */
    Flux<NoteSyncHistory> findByUserId(String userId);

    /**
     * Find sync records by status
     */
    Flux<NoteSyncHistory> findBySyncStatus(String syncStatus);

    /**
     * Check if a note has been successfully synced
     */
    Mono<Boolean> existsByNoteIdAndSyncStatus(String noteId, String syncStatus);

    /**
     * Check if note has successful sync
     */
    default Mono<Boolean> isNoteSynced(String noteId) {
        return existsByNoteIdAndSyncStatus(noteId, NoteSyncHistory.STATUS_SUCCESS);
    }
}

