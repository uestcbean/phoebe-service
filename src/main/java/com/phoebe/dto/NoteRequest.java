package com.phoebe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public class NoteRequest {

    @NotNull(message = "UserId is required")
    private Long userId;

    @NotBlank(message = "Source is required")
    private String source;

    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    private String comment; // 记录者的评论

    private List<String> tags;

    @NotNull(message = "CreatedAt is required")
    private OffsetDateTime createdAt;  // Keep OffsetDateTime for API compatibility

    public NoteRequest() {
    }

    public NoteRequest(Long userId, String source, String title, String content, String comment, List<String> tags, OffsetDateTime createdAt) {
        this.userId = userId;
        this.source = source;
        this.title = title;
        this.content = content;
        this.comment = comment;
        this.tags = tags;
        this.createdAt = createdAt;
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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Convert OffsetDateTime to LocalDateTime for database storage
     */
    public LocalDateTime getCreatedAtAsLocalDateTime() {
        return createdAt != null ? createdAt.toLocalDateTime() : null;
    }
}
