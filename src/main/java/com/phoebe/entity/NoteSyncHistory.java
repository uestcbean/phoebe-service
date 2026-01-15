package com.phoebe.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Entity tracking the sync history of notes to Bailian knowledge base.
 */
@Table("note_sync_history")
public class NoteSyncHistory implements Persistable<String> {

    @Id
    private String id;

    @Transient
    private boolean isNew = true;

    @Column("note_id")
    private String noteId;

    @Column("user_id")
    private String userId;

    @Column("index_id")
    private String indexId;

    @Column("document_id")
    private String documentId;  // 百炼返回的文档ID

    @Column("sync_status")
    private String syncStatus;  // PENDING, SUCCESS, FAILED

    @Column("error_message")
    private String errorMessage;

    @Column("synced_at")
    private OffsetDateTime syncedAt;

    // Status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public NoteSyncHistory() {
    }

    public NoteSyncHistory(String id, String noteId, String userId, String indexId, 
                           String syncStatus) {
        this.id = id;
        this.noteId = noteId;
        this.userId = userId;
        this.indexId = indexId;
        this.syncStatus = syncStatus;
        this.syncedAt = OffsetDateTime.now();
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
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

    public OffsetDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(OffsetDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }
}

