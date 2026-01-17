package com.phoebe.entity;

import java.time.LocalDateTime;

/**
 * Entity representing a note.
 */
public class Note {

    private Long id;
    private Long userId;
    private String source;
    private String title;
    private String content;
    private String comment; // 记录者的评论
    private String tags; // JSON string array
    private Integer status; // 0: deleted, 1: active
    private LocalDateTime createdAt;
    private LocalDateTime ingestedAt;

    // Note status constants
    public static final int STATUS_DELETED = 0;
    public static final int STATUS_ACTIVE = 1;

    public Note() {
    }

    public Note(Long userId, String source, String title, String content, 
                String comment, String tags, Integer status, LocalDateTime createdAt, LocalDateTime ingestedAt) {
        this.userId = userId;
        this.source = source;
        this.title = title;
        this.content = content;
        this.comment = comment;
        this.tags = tags;
        this.status = status;
        this.createdAt = createdAt;
        this.ingestedAt = ingestedAt;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(LocalDateTime ingestedAt) {
        this.ingestedAt = ingestedAt;
    }
}
