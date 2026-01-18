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
    public NoteResponse createNote(Long userId, NoteRequest request) {
        String tagsJson = serializeTags(request.getTags());
        
        // Use provided createdAt or default to current time
        LocalDateTime createdAt = request.getCreatedAtAsLocalDateTime();
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        Note note = new Note(
                userId,
                request.getSource(),
                request.getTitle(),
                request.getContent(),
                request.getComment(),
                tagsJson,
                Note.STATUS_ACTIVE,  // Default status is active (1)
                createdAt,
                LocalDateTime.now()
        );

        log.info("Creating note for userId: {}, source: {}", userId, request.getSource());

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
     * Get a single note by ID and user ID
     */
    public Note getNoteByIdAndUserId(Long noteId, Long userId) {
        return noteMapper.findByIdAndUserId(noteId, userId);
    }

    /**
     * Find all notes
     */
    public List<Note> findAll() {
        return noteMapper.findAll();
    }

    /**
     * Update an existing note
     */
    @Transactional
    public Note updateNote(Long noteId, Long userId, NoteRequest request) {
        Note note = noteMapper.findByIdAndUserId(noteId, userId);
        
        if (note == null) {
            log.warn("Note not found for update: noteId={}, userId={}", noteId, userId);
            return null;
        }
        
        // Update fields
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setComment(request.getComment());
        note.setTags(serializeTags(request.getTags()));
        if (request.getSource() != null) {
            note.setSource(request.getSource());
        }
        note.setIngestedAt(LocalDateTime.now());
        
        noteMapper.update(note);
        log.info("Note updated successfully: noteId={}", noteId);
        
        return note;
    }

    /**
     * Soft delete a note (set status to 0)
     */
    @Transactional
    public NoteResponse deleteNote(Long userId, Long noteId) {
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
