package com.phoebe.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class ChatRequest {

    private String sessionId;

    @NotBlank(message = "Message is required")
    private String message;

    /**
     * 会话主题/专注领域（如：技术、学习、日常、创作等）
     * 会影响 RAG 检索和回复内容的侧重点
     */
    private String topic;

    /**
     * Whether to enable RAG (knowledge base retrieval) for this request.
     */
    private boolean enableRag = true;

    /**
     * History of previous messages in this session.
     * Note: History messages should NOT include multimodal data (images/audio/files).
     */
    private List<ChatMessage> history;

    // ========== Multimodal Input Fields ==========
    // Only ONE of the following can be set at a time

    /**
     * Image data as base64 encoded string.
     * When set, uses qwen-vl-max model for vision understanding.
     */
    private String imageBase64;

    /**
     * Image MIME type (e.g., "image/png", "image/jpeg").
     */
    private String imageMimeType;

    /**
     * Audio data as base64 encoded string.
     * When set, uses qwen2-audio-instruct model for audio understanding.
     */
    private String audioBase64;

    /**
     * Audio format (e.g., "wav", "mp3", "webm").
     */
    private String audioFormat;

    /**
     * File content as text (extracted from uploaded file).
     * When set, the text content will be included in the message context.
     */
    private String fileContent;

    /**
     * Original file name for reference.
     */
    private String fileName;

    /**
     * Multimodal input type indicator.
     * Values: "text", "image", "audio", "file"
     */
    private String inputType = "text";

    public ChatRequest() {
    }

    public ChatRequest(String sessionId, String message) {
        this.sessionId = sessionId;
        this.message = message;
    }

    public ChatRequest(String sessionId, String message, boolean enableRag) {
        this.sessionId = sessionId;
        this.message = message;
        this.enableRag = enableRag;
    }

    // Getters and Setters

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isEnableRag() {
        return enableRag;
    }

    public void setEnableRag(boolean enableRag) {
        this.enableRag = enableRag;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<ChatMessage> getHistory() {
        return history;
    }

    public void setHistory(List<ChatMessage> history) {
        this.history = history;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public void setImageMimeType(String imageMimeType) {
        this.imageMimeType = imageMimeType;
    }

    public String getAudioBase64() {
        return audioBase64;
    }

    public void setAudioBase64(String audioBase64) {
        this.audioBase64 = audioBase64;
    }

    public String getAudioFormat() {
        return audioFormat;
    }

    public void setAudioFormat(String audioFormat) {
        this.audioFormat = audioFormat;
    }

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    /**
     * Check if this request contains image data.
     */
    public boolean hasImage() {
        return imageBase64 != null && !imageBase64.isBlank();
    }

    /**
     * Check if this request contains audio data.
     */
    public boolean hasAudio() {
        return audioBase64 != null && !audioBase64.isBlank();
    }

    /**
     * Check if this request contains file content.
     */
    public boolean hasFile() {
        return fileContent != null && !fileContent.isBlank();
    }

    /**
     * Represents a single message in the conversation history.
     * Note: History messages contain only text, no multimodal data.
     */
    public static class ChatMessage {
        private String role;  // "user" or "assistant"
        private String content;

        public ChatMessage() {
        }

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
