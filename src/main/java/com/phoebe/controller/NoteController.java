package com.phoebe.controller;

import com.phoebe.context.RequestUserHolder;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

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
    public NoteResponse createNote(ServerWebExchange exchange, @Valid @RequestBody NoteRequest request) {
        Long userId = RequestUserHolder.getUserId(exchange);
        return noteService.createNote(userId, request);
    }

    /**
     * Get all active notes for current user
     */
    @GetMapping
    public List<Note> getActiveNotes(ServerWebExchange exchange, @RequestParam(required = false) String source) {
        Long userId = RequestUserHolder.getUserId(exchange);
        if (source != null && !source.isBlank()) {
            return noteService.getActiveNotesBySource(userId, source);
        }
        return noteService.getActiveNotes(userId);
    }

    /**
     * Get a single note by ID
     */
    @GetMapping("/{noteId}")
    public Note getNoteById(ServerWebExchange exchange, @PathVariable Long noteId) {
        Long userId = RequestUserHolder.getUserId(exchange);
        return noteService.getNoteByIdAndUserId(noteId, userId);
    }

    /**
     * Update an existing note
     */
    @PutMapping("/{noteId}")
    public Note updateNote(ServerWebExchange exchange, @PathVariable Long noteId, @Valid @RequestBody NoteRequest request) {
        Long userId = RequestUserHolder.getUserId(exchange);
        return noteService.updateNote(noteId, userId, request);
    }

    /**
     * Soft delete a note
     */
    @DeleteMapping("/{noteId}")
    public NoteResponse deleteNote(ServerWebExchange exchange, @PathVariable Long noteId) {
        Long userId = RequestUserHolder.getUserId(exchange);
        return noteService.deleteNote(userId, noteId);
    }
}
