package com.phoebe.repository;

import com.phoebe.entity.Note;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Repository
public interface NoteRepository extends ReactiveCrudRepository<Note, String> {

    // Query by userId and status (active notes only)
    Flux<Note> findByUserIdAndStatus(String userId, Integer status);

    // Query all active notes for a user
    default Flux<Note> findActiveByUserId(String userId) {
        return findByUserIdAndStatus(userId, 1);
    }

    // Query by source and status
    Flux<Note> findBySourceAndStatus(String source, Integer status);

    // Query by userId, source and status
    Flux<Note> findByUserIdAndSourceAndStatus(String userId, String source, Integer status);

    // Query by date range and status
    Flux<Note> findByCreatedAtBetweenAndStatus(OffsetDateTime start, OffsetDateTime end, Integer status);

    // Query by userId, date range and status
    Flux<Note> findByUserIdAndCreatedAtBetweenAndStatus(String userId, OffsetDateTime start, OffsetDateTime end, Integer status);

    // Find a specific note by id and userId (for ownership verification)
    Mono<Note> findByIdAndUserId(String id, String userId);

    // Legacy methods (deprecated, prefer status-aware methods)
    @Deprecated
    Flux<Note> findBySource(String source);

    @Deprecated
    Flux<Note> findByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end);
}
