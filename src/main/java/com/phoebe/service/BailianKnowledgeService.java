package com.phoebe.service;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.bailian20231229.AsyncClient;
import com.aliyun.sdk.service.bailian20231229.models.*;
import com.phoebe.config.BailianConfig;
import com.phoebe.dto.bailian.RetrieveResult;
import com.phoebe.entity.BailianIndexPool;
import com.phoebe.entity.Note;
import com.phoebe.entity.NoteSyncHistory;
import com.phoebe.entity.UserKnowledgeBase;
import com.phoebe.mapper.BailianIndexPoolMapper;
import com.phoebe.mapper.NoteSyncHistoryMapper;
import com.phoebe.mapper.UserKnowledgeBaseMapper;
import darabonba.core.client.ClientOverrideConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for interacting with Aliyun Bailian Knowledge Base API.
 * Uses the official Aliyun Bailian SDK.
 * 
 * This service provides:
 * - Knowledge base creation for new users
 * - Document upload (notes) to user's knowledge base
 * - Knowledge retrieval for RAG
 */
@Service
public class BailianKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(BailianKnowledgeService.class);
    private static final int API_TIMEOUT_SECONDS = 30;

    private final BailianConfig bailianConfig;
    private final UserKnowledgeBaseMapper userKnowledgeBaseMapper;
    private final NoteSyncHistoryMapper noteSyncHistoryMapper;
    private final BailianIndexPoolMapper indexPoolMapper;
    private final WebClient webClient;
    private volatile AsyncClient client;

    public BailianKnowledgeService(
            BailianConfig bailianConfig,
            UserKnowledgeBaseMapper userKnowledgeBaseMapper,
            NoteSyncHistoryMapper noteSyncHistoryMapper,
            BailianIndexPoolMapper indexPoolMapper) {
        this.bailianConfig = bailianConfig;
        this.userKnowledgeBaseMapper = userKnowledgeBaseMapper;
        this.noteSyncHistoryMapper = noteSyncHistoryMapper;
        this.indexPoolMapper = indexPoolMapper;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Lazy initialization of the Bailian client.
     */
    private AsyncClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = createClient();
                }
            }
        }
        return client;
    }

    private AsyncClient createClient() {
        try {
            String accessKeyId = bailianConfig.getAccessKeyId();
            String accessKeySecret = bailianConfig.getAccessKeySecret();
            
            if (accessKeyId == null || accessKeyId.isEmpty() || 
                accessKeySecret == null || accessKeySecret.isEmpty()) {
                log.error("Bailian credentials not configured");
                return null;
            }
            
            log.info("Creating Bailian client for region: {}", bailianConfig.getRegion());
            
            // Create credential
            Credential credential = Credential.builder()
                    .accessKeyId(accessKeyId)
                    .accessKeySecret(accessKeySecret)
                    .build();
            
            // Create credential provider
            StaticCredentialProvider credentialProvider = StaticCredentialProvider.create(credential);
            
            // Build client
            return AsyncClient.builder()
                    .region(bailianConfig.getRegion())
                    .credentialsProvider(credentialProvider)
                    .overrideConfiguration(
                            ClientOverrideConfiguration.create()
                                    .setEndpointOverride("bailian." + bailianConfig.getRegion() + ".aliyuncs.com")
                    )
                    .build();
        } catch (Exception e) {
            log.error("Failed to create Bailian client: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get or create knowledge base for a user.
     * Priority:
     * 1. First check existing UserKnowledgeBase record
     * 2. Then check if user has assigned index in bailian_index_pool
     * 3. Fall back to default index from config
     * 4. Otherwise try to create new index
     */
    @Transactional
    public UserKnowledgeBase getOrCreateKnowledgeBase(Long userId) {
        // First try to find existing UserKnowledgeBase record
        UserKnowledgeBase existing = userKnowledgeBaseMapper.findByUserId(userId);
        if (existing != null) {
            log.debug("Found existing knowledge base for user {}: indexId={}", userId, existing.getIndexId());
            return existing;
        }
        
        // Check if user has assigned index in the pool
        BailianIndexPool assignedIndex = indexPoolMapper.findByAssignedUserId(userId);
        if (assignedIndex != null) {
            log.info("Using assigned index {} from pool for user {}", assignedIndex.getIndexId(), userId);
            return createUserKnowledgeBaseRecord(userId, assignedIndex.getIndexId(), 
                    assignedIndex.getIndexName() != null ? assignedIndex.getIndexName() : "kb_" + userId);
        }
        
        // Fall back to default index from config
        String defaultIndexId = bailianConfig.getDefaultIndexId();
        if (defaultIndexId != null && !defaultIndexId.isEmpty()) {
            log.warn("User {} has no assigned index, using default index {}", userId, defaultIndexId);
            return createUserKnowledgeBaseRecord(userId, defaultIndexId, "default_kb");
        }
        
        // Otherwise try to create new index (will likely fail without files)
        log.warn("No index available for user {}, attempting to create new index", userId);
        return createKnowledgeBaseForUser(userId);
    }

    /**
     * Create a local record for user's knowledge base mapping.
     */
    private UserKnowledgeBase createUserKnowledgeBaseRecord(Long userId, String indexId, String indexName) {
        UserKnowledgeBase kb = new UserKnowledgeBase(
                userId,
                indexId,
                bailianConfig.getWorkspaceId(),
                indexName,
                UserKnowledgeBase.STATUS_ACTIVE
        );
        userKnowledgeBaseMapper.insert(kb);
        log.info("Created knowledge base record for user {} with index {}", userId, indexId);
        return kb;
    }

    /**
     * Create a new knowledge base for a user in Bailian.
     */
    private UserKnowledgeBase createKnowledgeBaseForUser(Long userId) {
        log.info("Creating knowledge base for user: {}", userId);
        
        AsyncClient asyncClient = getClient();
        if (asyncClient == null) {
            throw new RuntimeException("Bailian client not initialized. Check your credentials.");
        }
        
        // Index name must be 1-20 characters
        // Use short prefix + user ID to ensure uniqueness within limit
        String indexName = "kb_" + userId;  // e.g., "kb_123456"
        
        // Build CreateIndex request
        // Required parameters: name, structureType, sinkType, sourceType
        // embeddingModelName is required for vector search
        CreateIndexRequest request = CreateIndexRequest.builder()
                .workspaceId(bailianConfig.getWorkspaceId())
                .name(indexName)
                .structureType("unstructured")
                .sinkType("DEFAULT")
                .sourceType("DATA_CENTER_FILE")
                .embeddingModelName(bailianConfig.getEmbeddingModel())
                .description("Knowledge base for user: " + userId)
                .build();
        
        log.info("CreateIndex request - name: {}, workspaceId: {}, embeddingModel: {}", 
                indexName, bailianConfig.getWorkspaceId(), bailianConfig.getEmbeddingModel());
        
        try {
            CreateIndexResponse response = asyncClient.createIndex(request)
                    .get(API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            CreateIndexResponseBody body = response.getBody();
            log.info("CreateIndex response received - StatusCode: {}, RequestId: {}, Code: {}, Message: {}, Success: {}, Status: {}",
                    response.getStatusCode(),
                    body != null ? body.getRequestId() : "null",
                    body != null ? body.getCode() : "null",
                    body != null ? body.getMessage() : "null",
                    body != null ? body.getSuccess() : "null",
                    body != null ? body.getStatus() : "null");
            
            // Check HTTP status code first
            if (response.getStatusCode() != 200) {
                String errorMsg = body != null ? body.getMessage() : "Unknown error";
                String errorCode = body != null ? body.getCode() : String.valueOf(response.getStatusCode());
                log.error("Bailian API HTTP error - StatusCode: {}, Code: {}, Message: {}", 
                        response.getStatusCode(), errorCode, errorMsg);
                throw new RuntimeException("Bailian API error (HTTP " + response.getStatusCode() + "): " + errorCode + " - " + errorMsg);
            }
            
            // Check for API error response
            if (body != null && Boolean.FALSE.equals(body.getSuccess())) {
                log.error("Bailian API error - Code: {}, Message: {}", body.getCode(), body.getMessage());
                throw new RuntimeException("Bailian API error: " + body.getCode() + " - " + body.getMessage());
            }
            
            if (body == null || body.getData() == null) {
                log.error("Failed to create knowledge base, empty response. RequestId: {}", 
                        body != null ? body.getRequestId() : "null");
                throw new RuntimeException("Failed to create knowledge base: empty response");
            }
            
            String indexId = body.getData().getId();
            if (indexId == null || indexId.isEmpty()) {
                log.error("Failed to create knowledge base, no index ID returned. Message: {}", 
                        body.getMessage());
                throw new RuntimeException("Failed to create knowledge base: " + body.getMessage());
            }
            
            log.info("Created knowledge base with indexId: {} for user: {}", indexId, userId);
            
            UserKnowledgeBase kb = new UserKnowledgeBase(
                    userId,
                    indexId,
                    bailianConfig.getWorkspaceId(),
                    indexName,
                    UserKnowledgeBase.STATUS_ACTIVE
            );
            
            userKnowledgeBaseMapper.insert(kb);
            log.info("Saved knowledge base {} for user {}", kb.getIndexId(), userId);
            return kb;
            
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Failed to create knowledge base for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to create knowledge base: " + e.getMessage(), e);
        }
    }

    /**
     * Add a note to user's knowledge base.
     * Complete flow:
     * 1. ApplyFileUploadLease - get upload credentials
     * 2. Upload to OSS using the credentials
     * 3. AddFile - register file in data center
     * 4. SubmitIndexAddDocumentsJob - add to knowledge base index
     */
    @Transactional
    public NoteSyncHistory addNoteToKnowledgeBase(Note note) {
        Long noteId = note.getId();
        Long userId = note.getUserId();
        
        log.info("Adding note {} to knowledge base for user {}", noteId, userId);
        
        AsyncClient asyncClient = getClient();
        if (asyncClient == null) {
            log.error("Bailian client not initialized for note sync");
            return createFailedSyncHistory(noteId, userId, "", "Bailian client not initialized");
        }
        
        try {
            // Build document content
            String documentContent = buildDocumentContent(note);
            byte[] contentBytes = documentContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String fileName = "note_" + noteId + ".txt";
            String md5 = calculateMd5(contentBytes);
            
            UserKnowledgeBase kb = getOrCreateKnowledgeBase(userId);
            
            // Get category ID from user's assigned index pool entry, fallback to default
            String categoryId = getCategoryIdForUser(userId);
            if (categoryId == null || categoryId.isEmpty()) {
                throw new RuntimeException("No category ID found for user " + userId + ". Please ensure user has an assigned index or configure bailian.default-category-id");
            }
            
            log.info("Uploading note {} ({} bytes) to category {}, index {}", noteId, contentBytes.length, categoryId, kb.getIndexId());
            
            // Step 1: Apply for upload lease
            ApplyFileUploadLeaseRequest leaseRequest = ApplyFileUploadLeaseRequest.builder()
                    .workspaceId(bailianConfig.getWorkspaceId())
                    .categoryId(categoryId)  // Use category from data center
                    .categoryType("UNSTRUCTURED")
                    .fileName(fileName)
                    .md5(md5)
                    .sizeInBytes(String.valueOf(contentBytes.length))
                    .build();
            
            ApplyFileUploadLeaseResponse leaseResponse = asyncClient.applyFileUploadLease(leaseRequest)
                    .get(API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            ApplyFileUploadLeaseResponseBody leaseBody = leaseResponse.getBody();
            log.info("ApplyFileUploadLease response - Success: {}, Code: {}, Message: {}",
                    leaseBody != null ? leaseBody.getSuccess() : null,
                    leaseBody != null ? leaseBody.getCode() : null,
                    leaseBody != null ? leaseBody.getMessage() : null);
            
            if (leaseBody == null || leaseBody.getData() == null) {
                throw new RuntimeException("Failed to get upload lease: " + 
                        (leaseBody != null ? leaseBody.getMessage() : "empty response"));
            }
            
            ApplyFileUploadLeaseResponseBody.Data data = leaseBody.getData();
            String leaseId = data.getFileUploadLeaseId();
            ApplyFileUploadLeaseResponseBody.Param param = data.getParam();
            
            if (param == null || param.getUrl() == null) {
                throw new RuntimeException("No upload URL in lease response");
            }
            
            log.info("Got upload lease: {}, URL: {}", leaseId, param.getUrl());
            
            // Step 2: Upload file to OSS
            uploadToOss(param.getUrl(), param.getMethod(), param.getHeaders(), contentBytes);
            
            // Step 3: Register file with AddFile
            AddFileRequest addFileRequest = AddFileRequest.builder()
                    .workspaceId(bailianConfig.getWorkspaceId())
                    .categoryId(categoryId)  // Use category from data center
                    .leaseId(leaseId)
                    .parser("DASHSCOPE_DOCMIND")
                    .build();
            
            AddFileResponse addFileResponse = asyncClient.addFile(addFileRequest)
                    .get(API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            AddFileResponseBody addBody = addFileResponse.getBody();
            log.info("AddFile response - Success: {}, FileId: {}",
                    addBody != null ? addBody.getSuccess() : null,
                    addBody != null && addBody.getData() != null ? addBody.getData().getFileId() : null);
            
            String fileId = addBody != null && addBody.getData() != null ? 
                    addBody.getData().getFileId() : null;
            
            if (fileId == null) {
                throw new RuntimeException("Failed to add file, no fileId returned");
            }
            
            // Step 4: Add document to index
            SubmitIndexAddDocumentsJobRequest indexRequest = SubmitIndexAddDocumentsJobRequest.builder()
                    .workspaceId(bailianConfig.getWorkspaceId())
                    .indexId(kb.getIndexId())
                    .sourceType("DATA_CENTER_FILE")
                    .documentIds(Collections.singletonList(fileId))
                    .build();
            
            SubmitIndexAddDocumentsJobResponse indexResponse = asyncClient.submitIndexAddDocumentsJob(indexRequest)
                    .get(API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            SubmitIndexAddDocumentsJobResponseBody indexBody = indexResponse.getBody();
            log.info("SubmitIndexAddDocumentsJob response - Success: {}, JobId: {}",
                    indexBody != null ? indexBody.getSuccess() : null,
                    indexBody != null && indexBody.getData() != null ? indexBody.getData().getId() : null);
            
            // Create success sync record
            NoteSyncHistory history = new NoteSyncHistory(
                    noteId,
                    userId,
                    kb.getIndexId(),
                    NoteSyncHistory.STATUS_SUCCESS
            );
            history.setDocumentId(fileId);
            noteSyncHistoryMapper.insert(history);
            
            log.info("Successfully synced note {} to knowledge base, fileId: {}", noteId, history.getDocumentId());
            return history;
            
        } catch (Exception e) {
            log.error("Failed to add note {} to knowledge base: {}", noteId, e.getMessage(), e);
            return createFailedSyncHistory(noteId, userId, "", e.getMessage());
        }
    }

    /**
     * Upload file content to OSS using the provided credentials.
     */
    private void uploadToOss(String url, String method, Object headersObj, byte[] content) {
        log.info("Uploading {} bytes to OSS: {}", content.length, url);
        
        // Use URI.create() to prevent double URL encoding
        // The URL from Bailian API is already properly encoded
        java.net.URI uri = java.net.URI.create(url);
        
        WebClient.RequestBodySpec requestSpec;
        
        if ("PUT".equalsIgnoreCase(method)) {
            requestSpec = webClient.put().uri(uri);
        } else {
            requestSpec = webClient.post().uri(uri);
        }
        
        // Add headers from the lease response
        if (headersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) headersObj;
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestSpec = requestSpec.header(entry.getKey(), entry.getValue());
            }
        }
        
        requestSpec
                .bodyValue(content)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("OSS upload successful, status: {}", response.getStatusCode()))
                .doOnError(e -> log.error("OSS upload failed: {}", e.getMessage()))
                .block(Duration.ofSeconds(API_TIMEOUT_SECONDS));
        
        log.info("OSS upload completed");
    }

    /**
     * Calculate MD5 hash of content.
     */
    private String calculateMd5(byte[] content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to calculate MD5", e);
            return "";
        }
    }

    /**
     * Retrieve relevant information from user's knowledge base.
     * Used for RAG - retrieve context before sending to LLM.
     */
    public RetrieveResult retrieve(Long userId, String query) {
        log.debug("Retrieving from knowledge base for user {}, query length: {}", 
                userId, query.length());
        
        AsyncClient asyncClient = getClient();
        if (asyncClient == null) {
            log.warn("Bailian client not initialized, returning empty result");
            return new RetrieveResult(Collections.emptyList(), null);
        }
        
        try {
            UserKnowledgeBase kb = userKnowledgeBaseMapper.findByUserId(userId);
            if (kb == null) {
                log.info("No knowledge base found for user {}, returning empty result", userId);
                return new RetrieveResult(Collections.emptyList(), null);
            }
            
            log.info("Retrieving from index {} for user {}", kb.getIndexId(), userId);
            
            // Build retrieve request
            RetrieveRequest request = RetrieveRequest.builder()
                    .workspaceId(bailianConfig.getWorkspaceId())
                    .indexId(kb.getIndexId())
                    .query(query)
                    .denseSimilarityTopK(bailianConfig.getRetrieveTopK())
                    .build();
            
            RetrieveResponse response = asyncClient.retrieve(request)
                    .get(API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            RetrieveResult result = parseRetrieveResponse(response);
            log.info("Retrieved {} nodes from knowledge base for user {}", 
                    result.getNodes() != null ? result.getNodes().size() : 0, userId);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to retrieve from knowledge base for user {}: {}", userId, e.getMessage());
            return new RetrieveResult(Collections.emptyList(), null);
        }
    }

    /**
     * Check if a note has been synced to knowledge base.
     */
    public boolean isNoteSynced(Long noteId) {
        return noteSyncHistoryMapper.isNoteSynced(noteId);
    }

    /**
     * Get the latest successful sync history for a note.
     */
    public NoteSyncHistory getLatestSyncHistory(Long noteId) {
        return noteSyncHistoryMapper.findLatestSuccessfulByNoteId(noteId);
    }

    /**
     * Get the category ID for a user from database.
     * First tries to get from user's assigned index pool entry,
     * falls back to default category ID if not found.
     * 
     * @param userId The user ID
     * @return The category ID from database or default config
     */
    private String getCategoryIdForUser(Long userId) {
        // Try to get from user's assigned index in the pool
        BailianIndexPool assigned = indexPoolMapper.findByAssignedUserId(userId);
        if (assigned != null && assigned.getCategoryId() != null) {
            log.debug("Found category ID {} from index pool for user {}", assigned.getCategoryId(), userId);
            return assigned.getCategoryId();
        }
        
        // Fallback to default from config
        String defaultCategoryId = bailianConfig.getDefaultCategoryId();
        log.warn("User {} has no assigned category ID in pool, using default: {}", userId, defaultCategoryId);
        return defaultCategoryId;
    }

    /**
     * Get the index ID for a user from database.
     * First tries to get from user's assigned index pool entry,
     * falls back to default index ID if not found.
     * 
     * @param userId The user ID
     * @return The index ID from database or default config
     */
    private String getIndexIdForUser(Long userId) {
        // Try to get from user's assigned index in the pool
        BailianIndexPool assigned = indexPoolMapper.findByAssignedUserId(userId);
        if (assigned != null && assigned.getIndexId() != null) {
            log.debug("Found index ID {} from index pool for user {}", assigned.getIndexId(), userId);
            return assigned.getIndexId();
        }
        
        // Fallback to default from config
        String defaultIndexId = bailianConfig.getDefaultIndexId();
        log.warn("User {} has no assigned index ID in pool, using default: {}", userId, defaultIndexId);
        return defaultIndexId;
    }

    /**
     * Delete a document from the knowledge base index.
     * This removes the document from the index so it won't be retrieved.
     */
    public boolean deleteDocumentFromIndex(Long userId, String documentId) {
        log.info("Deleting document {} from knowledge base for user {}", documentId, userId);
        
        AsyncClient asyncClient = getClient();
        if (asyncClient == null) {
            log.error("Bailian client not initialized");
            return false;
        }
        
        try {
            UserKnowledgeBase kb = userKnowledgeBaseMapper.findByUserId(userId);
            if (kb == null) {
                log.warn("No knowledge base found for user {}", userId);
                return false;
            }
            
            // Use SubmitIndexJob with DELETE operation type to remove document from index
            // Note: We're using deleteFile to remove from data center
            DeleteFileRequest deleteRequest = DeleteFileRequest.builder()
                    .workspaceId(bailianConfig.getWorkspaceId())
                    .fileId(documentId)
                    .build();
            
            DeleteFileResponse deleteResponse = asyncClient.deleteFile(deleteRequest)
                    .get(API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            DeleteFileResponseBody body = deleteResponse.getBody();
            log.info("DeleteFile response - Success: {}, RequestId: {}",
                    body != null ? body.getSuccess() : null,
                    body != null ? body.getRequestId() : null);
            
            if (body != null && Boolean.TRUE.equals(body.getSuccess())) {
                log.info("Successfully deleted document {} from knowledge base", documentId);
                return true;
            } else {
                log.warn("Failed to delete document {}: {}", documentId, 
                        body != null ? body.getMessage() : "unknown error");
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error deleting document {} from knowledge base: {}", documentId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Update a note in the knowledge base.
     * This will delete the old document and add the new one.
     * 
     * @param note The note with updated content
     * @return The new sync history record
     */
    @Transactional
    public NoteSyncHistory updateNoteInKnowledgeBase(Note note) {
        Long noteId = note.getId();
        Long userId = note.getUserId();
        
        log.info("Updating note {} in knowledge base for user {}", noteId, userId);
        
        // Find existing sync record with document ID
        NoteSyncHistory existingSync = noteSyncHistoryMapper.findLatestSuccessfulByNoteId(noteId);
        
        if (existingSync == null || existingSync.getDocumentId() == null) {
            log.info("No existing sync record found for note {}, will add as new document", noteId);
            return addNoteToKnowledgeBase(note);
        }
        
        String oldDocumentId = existingSync.getDocumentId();
        log.info("Found existing document {} for note {}, will delete and re-add", oldDocumentId, noteId);
        
        // Step 1: Delete old document from knowledge base
        boolean deleted = deleteDocumentFromIndex(userId, oldDocumentId);
        if (!deleted) {
            log.warn("Failed to delete old document {}, but will continue to add new document", oldDocumentId);
            // Don't fail here - the old document might already be deleted
            // Just continue to add the new document
        }
        
        // Step 2: Add the updated note as new document
        NoteSyncHistory newHistory = addNoteToKnowledgeBase(note);
        
        if (newHistory != null && NoteSyncHistory.STATUS_SUCCESS.equals(newHistory.getSyncStatus())) {
            log.info("Successfully updated note {} in knowledge base, new documentId: {}", 
                    noteId, newHistory.getDocumentId());
        } else {
            log.error("Failed to add updated note {} to knowledge base", noteId);
        }
        
        return newHistory;
    }

    /**
     * Build document content from a note for knowledge base.
     */
    private String buildDocumentContent(Note note) {
        StringBuilder sb = new StringBuilder();
        
        if (note.getTitle() != null && !note.getTitle().isEmpty()) {
            sb.append("标题: ").append(note.getTitle()).append("\n\n");
        }
        
        if (note.getContent() != null && !note.getContent().isEmpty()) {
            sb.append("内容: ").append(note.getContent()).append("\n\n");
        }
        
        if (note.getComment() != null && !note.getComment().isEmpty()) {
            sb.append("评论: ").append(note.getComment()).append("\n\n");
        }
        
        if (note.getTags() != null && !note.getTags().isEmpty()) {
            sb.append("标签: ").append(note.getTags()).append("\n\n");
        }
        
        if (note.getSource() != null && !note.getSource().isEmpty()) {
            sb.append("来源: ").append(note.getSource()).append("\n");
        }
        
        if (note.getCreatedAt() != null) {
            sb.append("创建时间: ").append(note.getCreatedAt().toString()).append("\n");
        }
        
        return sb.toString();
    }

    /**
     * Parse the retrieve API response into RetrieveResult.
     */
    private RetrieveResult parseRetrieveResponse(RetrieveResponse response) {
        RetrieveResult result = new RetrieveResult();
        
        RetrieveResponseBody body = response.getBody();
        if (body == null) {
            result.setNodes(Collections.emptyList());
            return result;
        }
        
        result.setRequestId(body.getRequestId());
        
        List<RetrieveResult.RetrieveNode> nodes = new ArrayList<>();
        RetrieveResponseBody.Data data = body.getData();
        
        if (data != null && data.getNodes() != null) {
            for (RetrieveResponseBody.Nodes node : data.getNodes()) {
                RetrieveResult.RetrieveNode resultNode = new RetrieveResult.RetrieveNode();
                resultNode.setText(node.getText());
                resultNode.setScore(node.getScore() != null ? node.getScore() : 0.0);
                
                // Parse metadata if available
                Object metadata = node.getMetadata();
                if (metadata != null) {
                    RetrieveResult.Metadata resultMetadata = new RetrieveResult.Metadata();
                    // metadata is typically a Map
                    if (metadata instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metaMap = (Map<String, Object>) metadata;
                        Object docId = metaMap.get("docId");
                        Object docName = metaMap.get("docName");
                        Object title = metaMap.get("title");
                        resultMetadata.setDocumentId(docId != null ? String.valueOf(docId) : null);
                        resultMetadata.setDocumentName(docName != null ? String.valueOf(docName) : null);
                        resultMetadata.setTitle(title != null ? String.valueOf(title) : null);
                    }
                    resultNode.setMetadata(resultMetadata);
                }
                
                nodes.add(resultNode);
            }
        }
        
        result.setNodes(nodes);
        return result;
    }

    /**
     * Create a failed sync history record.
     */
    private NoteSyncHistory createFailedSyncHistory(Long noteId, Long userId, 
                                                     String indexId, String errorMessage) {
        NoteSyncHistory history = new NoteSyncHistory(
                noteId,
                userId,
                indexId,
                NoteSyncHistory.STATUS_FAILED
        );
        history.setErrorMessage(errorMessage);
        noteSyncHistoryMapper.insert(history);
        return history;
    }
}
