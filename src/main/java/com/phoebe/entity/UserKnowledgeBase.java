package com.phoebe.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Entity representing the mapping between a user and their Bailian knowledge base.
 * Each user has a unique knowledge base in Aliyun Bailian.
 */
@Table("user_knowledge_base")
public class UserKnowledgeBase implements Persistable<String> {

    @Id
    private String id;

    @Transient
    private boolean isNew = true;

    @Column("user_id")
    private String userId;

    @Column("index_id")
    private String indexId;  // 百炼知识库索引ID

    @Column("workspace_id")
    private String workspaceId;  // 百炼业务空间ID

    @Column("index_name")
    private String indexName;  // 知识库名称

    @Column("status")
    private String status;  // ACTIVE, DISABLED, ERROR

    @Column("last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    // Status constants
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_ERROR = "ERROR";

    public UserKnowledgeBase() {
    }

    public UserKnowledgeBase(String id, String userId, String indexId, String workspaceId, 
                              String indexName, String status) {
        this.id = id;
        this.userId = userId;
        this.indexId = indexId;
        this.workspaceId = workspaceId;
        this.indexName = indexName;
        this.status = status;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
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

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(OffsetDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

