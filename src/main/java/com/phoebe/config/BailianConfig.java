package com.phoebe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Aliyun Bailian Knowledge Base API.
 * 
 * Bailian (百炼) is Aliyun's enterprise AI platform that provides
 * knowledge base management and retrieval capabilities.
 */
@Configuration
@ConfigurationProperties(prefix = "bailian")
public class BailianConfig {

    /**
     * Aliyun Access Key ID for authentication
     */
    private String accessKeyId;

    /**
     * Aliyun Access Key Secret for authentication
     */
    private String accessKeySecret;

    /**
     * API endpoint region (e.g., cn-beijing, cn-hangzhou)
     */
    private String region = "cn-beijing";

    /**
     * Default workspace ID (业务空间ID)
     */
    private String workspaceId;

    /**
     * API endpoint URL
     */
    private String endpoint = "bailian.cn-beijing.aliyuncs.com";

    /**
     * Embedding model for knowledge base
     */
    private String embeddingModel = "text-embedding-v2";

    /**
     * Number of documents to retrieve in RAG
     */
    private int retrieveTopK = 5;

    /**
     * Minimum similarity score for retrieval
     */
    private double retrieveMinScore = 0.5;

    /**
     * Whether knowledge base sync is enabled
     */
    private boolean syncEnabled = true;

    /**
     * Cron expression for sync schedule (default: daily at 2 AM)
     */
    private String syncCron = "0 0 2 * * ?";

    /**
     * Default index ID for knowledge base.
     * If set, all users will share this knowledge base (with user-specific filtering).
     * Create the knowledge base manually in Bailian console first.
     */
    private String defaultIndexId;

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getRetrieveTopK() {
        return retrieveTopK;
    }

    public void setRetrieveTopK(int retrieveTopK) {
        this.retrieveTopK = retrieveTopK;
    }

    public double getRetrieveMinScore() {
        return retrieveMinScore;
    }

    public void setRetrieveMinScore(double retrieveMinScore) {
        this.retrieveMinScore = retrieveMinScore;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public String getSyncCron() {
        return syncCron;
    }

    public void setSyncCron(String syncCron) {
        this.syncCron = syncCron;
    }

    public String getDefaultIndexId() {
        return defaultIndexId;
    }

    public void setDefaultIndexId(String defaultIndexId) {
        this.defaultIndexId = defaultIndexId;
    }
}

