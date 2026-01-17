package com.phoebe.entity;

import java.time.LocalDateTime;

/**
 * Entity representing a Bailian knowledge base index in the pool.
 * Each index can be assigned to one user during registration.
 */
public class BailianIndexPool {

    private Long id;
    private String indexId;        // 百炼知识库索引ID (e.g., m71tmd04g9)
    private String categoryId;     // 百炼数据中心类目ID
    private String indexName;      // 索引名称描述
    private String status;         // AVAILABLE, ASSIGNED, DISABLED
    private Long assignedUserId;   // 分配给的用户ID
    private LocalDateTime assignedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Status constants
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_DISABLED = "DISABLED";

    public BailianIndexPool() {
    }

    public BailianIndexPool(String indexId, String categoryId, String indexName) {
        this.indexId = indexId;
        this.categoryId = categoryId;
        this.indexName = indexName;
        this.status = STATUS_AVAILABLE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIndexId() {
        return indexId;
    }

    public void setIndexId(String indexId) {
        this.indexId = indexId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
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

    public Long getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(Long assignedUserId) {
        this.assignedUserId = assignedUserId;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
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

    @Override
    public String toString() {
        return "BailianIndexPool{" +
                "id=" + id +
                ", indexId='" + indexId + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", status='" + status + '\'' +
                ", assignedUserId=" + assignedUserId +
                '}';
    }
}
