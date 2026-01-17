package com.phoebe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoebe.dto.NoteRequest;
import com.phoebe.dto.NoteResponse;
import com.phoebe.entity.Note;
import com.phoebe.mapper.NoteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);
    private final NoteMapper noteMapper;
    private final ObjectMapper objectMapper;

    public NoteService(NoteMapper noteMapper, ObjectMapper objectMapper) {
        this.noteMapper = noteMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NoteResponse createNote(NoteRequest request) {
        String tagsJson = serializeTags(request.getTags());

        Note note = new Note(
                request.getUserId(),
                request.getSource(),
                request.getTitle(),
                request.getContent(),
                request.getComment(),
                tagsJson,
                Note.STATUS_ACTIVE,  // Default status is active (1)
                request.getCreatedAtAsLocalDateTime(),  // Convert OffsetDateTime to LocalDateTime
                LocalDateTime.now()
        );

        log.info("Creating note for userId: {}, source: {}", request.getUserId(), request.getSource());

        noteMapper.insert(note);
        log.info("Note saved successfully: id={}", note.getId());
        return NoteResponse.stored(note.getId());
    }

    /**
     * Get all active notes for a user
     */
    public List<Note> getActiveNotes(Long userId) {
        return noteMapper.findActiveByUserId(userId);
    }

    /**
     * Get active notes by userId and source
     */
    public List<Note> getActiveNotesBySource(Long userId, String source) {
        return noteMapper.findByUserIdAndSourceAndStatus(userId, source, Note.STATUS_ACTIVE);
    }

    /**
     * Find note by ID
     */
    public Note findById(Long noteId) {
        return noteMapper.findById(noteId);
    }

    /**
     * Find all notes
     */
    public List<Note> findAll() {
        return noteMapper.findAll();
    }

    /**
     * Soft delete a note (set status to 0)
     */
    @Transactional
    public NoteResponse deleteNote(Long noteId, Long userId) {
        Note note = noteMapper.findByIdAndUserId(noteId, userId);
        
        if (note == null) {
            return new NoteResponse(noteId, "not_found");
        }
        
        if (note.getStatus() == Note.STATUS_DELETED) {
            return new NoteResponse(noteId, "already_deleted");
        }
        
        note.setStatus(Note.STATUS_DELETED);
        noteMapper.update(note);
        log.info("Note soft deleted: {}", noteId);
        return new NoteResponse(noteId, "deleted");
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
