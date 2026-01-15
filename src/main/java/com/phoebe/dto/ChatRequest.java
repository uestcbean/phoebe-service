package com.phoebe.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {

    private String sessionId;

    @NotBlank(message = "User ID is required")
    private String userId;

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

    public ChatRequest(String sessionId, String userId, String message) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.message = message;
    }

    public ChatRequest(String sessionId, String userId, String message, boolean enableRag) {
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
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
