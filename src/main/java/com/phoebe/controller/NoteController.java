package com.phoebe.controller;

import com.phoebe.dto.NoteRequest;
import com.phoebe.dto.NoteResponse;
import com.phoebe.entity.Note;
import com.phoebe.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse createNote(@Valid @RequestBody NoteRequest request) {
        return noteService.createNote(request);
    }

    /**
     * Get all active notes for a user
     */
    @GetMapping
    public List<Note> getActiveNotes(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String source) {
        if (source != null && !source.isBlank()) {
            return noteService.getActiveNotesBySource(userId, source);
        }
        return noteService.getActiveNotes(userId);
    }

    /**
     * Soft delete a note
     */
    @DeleteMapping("/{noteId}")
    public NoteResponse deleteNote(
            @PathVariable Long noteId,
            @RequestHeader("X-User-Id") Long userId) {
        return noteService.deleteNote(noteId, userId);
    }
}
