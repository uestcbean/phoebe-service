package com.phoebe.entity;

import java.time.LocalDateTime;

/**
 * Entity tracking the sync history of notes to Bailian knowledge base.
 */
public class NoteSyncHistory {

    private Long id;
    private Long noteId;
    private Long userId;
    private String indexId;
    private String documentId;  // 百炼返回的文档ID
    private String syncStatus;  // PENDING, SUCCESS, FAILED
    private String errorMessage;
    private LocalDateTime syncedAt;

    // Status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public NoteSyncHistory() {
    }

    public NoteSyncHistory(Long noteId, Long userId, String indexId, String syncStatus) {
        this.noteId = noteId;
        this.userId = userId;
        this.indexId = indexId;
        this.syncStatus = syncStatus;
        this.syncedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getIndexId() {
        return indexId;
    }

    public void setIndexId(String indexId) {
        this.indexId = indexId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }
}
