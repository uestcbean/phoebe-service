package com.phoebe.entity;

import java.time.LocalDateTime;

/**
 * Entity representing the mapping between a user and their Bailian knowledge base.
 * Each user has a unique knowledge base in Aliyun Bailian.
 */
public class UserKnowledgeBase {

    private Long id;
    private Long userId;
    private String indexId;  // 百炼知识库索引ID
    private String workspaceId;  // 百炼业务空间ID
    private String indexName;  // 知识库名称
    private String status;  // ACTIVE, DISABLED, ERROR
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Status constants
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_ERROR = "ERROR";

    public UserKnowledgeBase() {
    }

    public UserKnowledgeBase(Long userId, String indexId, String workspaceId, 
                              String indexName, String status) {
        this.userId = userId;
        this.indexId = indexId;
        this.workspaceId = workspaceId;
        this.indexName = indexName;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
