package com.phoebe.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public class ChatRequest {

    private String sessionId;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Message is required")
    private String message;

    /**
     * Whether to enable RAG (knowledge base retrieval) for this request.
     * When enabled, the system will first retrieve relevant information from
     * the user's knowledge base and include it as context for the LLM.
     */
    private boolean enableRag = true;

    public ChatRequest() {
    }

    public ChatRequest(String sessionId, Long userId, String message) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.message = message;
    }

    public ChatRequest(String sessionId, Long userId, String message, boolean enableRag) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.message = message;
        this.enableRag = enableRag;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
}
