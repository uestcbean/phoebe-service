package com.phoebe.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("notes")
public class Note implements Persistable<String> {

    @Id
    private String id;

    @Transient
    private boolean isNew = true;

    @Column("user_id")
    private String userId;

    @Column("source")
    private String source;

    @Column("title")
    private String title;

    @Column("content")
    private String content;

    @Column("comment")
    private String comment; // 记录者的评论

    @Column("tags")
    private String tags; // JSON string array

    @Column("status")
    private Integer status; // 0: deleted, 1: active

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("ingested_at")
    private OffsetDateTime ingestedAt;

    // Note status constants
    public static final int STATUS_DELETED = 0;
    public static final int STATUS_ACTIVE = 1;

    public Note() {
    }

    public Note(String id, String userId, String source, String title, String content, String comment, String tags, Integer status, OffsetDateTime createdAt, OffsetDateTime ingestedAt) {
        this.id = id;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(OffsetDateTime ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}
