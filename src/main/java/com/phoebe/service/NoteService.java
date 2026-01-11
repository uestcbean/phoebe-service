package com.phoebe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoebe.dto.NoteRequest;
import com.phoebe.dto.NoteResponse;
import com.phoebe.entity.Note;
import com.phoebe.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);
    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper;

    public NoteService(NoteRepository noteRepository, ObjectMapper objectMapper) {
        this.noteRepository = noteRepository;
        this.objectMapper = objectMapper;
    }

    public Mono<NoteResponse> createNote(NoteRequest request) {
        String id = UUID.randomUUID().toString();
        String tagsJson = serializeTags(request.getTags());

        Note note = new Note(
                id,
                request.getUserId(),
                request.getSource(),
                request.getTitle(),
                request.getContent(),
                request.getComment(),
                tagsJson,
                Note.STATUS_ACTIVE,  // Default status is active (1)
                request.getCreatedAt(),
                OffsetDateTime.now()
        );

        log.info("Creating note with id: {}, userId: {}, source: {}", id, request.getUserId(), request.getSource());

        return noteRepository.save(note)
                .map(saved -> {
                    log.info("Note saved successfully: {}", saved.getId());
                    return NoteResponse.stored(saved.getId());
                });
    }

    /**
     * Get all active notes for a user
     */
    public Flux<Note> getActiveNotes(String userId) {
        return noteRepository.findActiveByUserId(userId);
    }

    /**
     * Get active notes by userId and source
     */
    public Flux<Note> getActiveNotesBySource(String userId, String source) {
        return noteRepository.findByUserIdAndSourceAndStatus(userId, source, Note.STATUS_ACTIVE);
    }

    /**
     * Soft delete a note (set status to 0)
     */
    public Mono<NoteResponse> deleteNote(String noteId, String userId) {
        return noteRepository.findByIdAndUserId(noteId, userId)
                .flatMap(note -> {
                    if (note.getStatus() == Note.STATUS_DELETED) {
                        return Mono.just(new NoteResponse(noteId, "already_deleted"));
                    }
                    note.setStatus(Note.STATUS_DELETED);
                    note.setNew(false);  // Mark as existing entity for update
                    return noteRepository.save(note)
                            .map(saved -> {
                                log.info("Note soft deleted: {}", noteId);
                                return new NoteResponse(noteId, "deleted");
                            });
                })
                .switchIfEmpty(Mono.just(new NoteResponse(noteId, "not_found")));
    }

    private String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tags", e);
            return "[]";
        }
    }
}
