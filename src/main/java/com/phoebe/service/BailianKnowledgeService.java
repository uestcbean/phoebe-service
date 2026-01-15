package com.phoebe.service;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.bailian20231229.AsyncClient;
import com.aliyun.sdk.service.bailian20231229.models.*;
import com.phoebe.config.BailianConfig;
import com.phoebe.dto.bailian.RetrieveResult;
import com.phoebe.entity.Note;
import com.phoebe.entity.NoteSyncHistory;
import com.phoebe.entity.UserKnowledgeBase;
import com.phoebe.repository.NoteSyncHistoryRepository;
import com.phoebe.repository.UserKnowledgeBaseRepository;
import darabonba.core.client.ClientOverrideConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

    private final BailianConfig bailianConfig;
    private final UserKnowledgeBaseRepository userKnowledgeBaseRepository;
    private final NoteSyncHistoryRepository noteSyncHistoryRepository;
    private volatile AsyncClient client;

    public BailianKnowledgeService(
            BailianConfig bailianConfig,
            UserKnowledgeBaseRepository userKnowledgeBaseRepository,
            NoteSyncHistoryRepository noteSyncHistoryRepository) {
        this.bailianConfig = bailianConfig;
        this.userKnowledgeBaseRepository = userKnowledgeBaseRepository;
        this.noteSyncHistoryRepository = noteSyncHistoryRepository;
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
     * If defaultIndexId is configured, use that shared index.
     * Otherwise, try to create a new index (requires files to be uploaded first).
     */
    public Mono<UserKnowledgeBase> getOrCreateKnowledgeBase(String userId) {
        return userKnowledgeBaseRepository.findByUserId(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    // Check if default index is configured
                    String defaultIndexId = bailianConfig.getDefaultIndexId();
                    if (defaultIndexId != null && !defaultIndexId.isEmpty()) {
                        log.info("Using default index {} for user {}", defaultIndexId, userId);
                        return createUserKnowledgeBaseRecord(userId, defaultIndexId, "default_kb");
                    }
                    // Otherwise try to create new index (will likely fail without files)
                    return createKnowledgeBaseForUser(userId);
                }));
    }

    /**
     * Create a local record for user's knowledge base mapping.
     */
    private Mono<UserKnowledgeBase> createUserKnowledgeBaseRecord(String userId, String indexId, String indexName) {
        UserKnowledgeBase kb = new UserKnowledgeBase(
                UUID.randomUUID().toString(),
                userId,
                indexId,
                bailianConfig.getWorkspaceId(),
                indexName,
                UserKnowledgeBase.STATUS_ACTIVE
        );
        return userKnowledgeBaseRepository.save(kb)
                .doOnSuccess(saved -> log.info("Created knowledge base record for user {} with index {}", userId, indexId));
    }

    /**
     * Create a new knowledge base for a user in Bailian.
     */
    private Mono<UserKnowledgeBase> createKnowledgeBaseForUser(String userId) {
        log.info("Creating knowledge base for user: {}", userId);
        
        AsyncClient asyncClient = getClient();
        if (asyncClient == null) {
            return Mono.error(new RuntimeException("Bailian client not initialized. Check your credentials."));
        }
        
        // Index name must be 1-20 characters
        // Use short prefix + hash of userId to ensure uniqueness within limit
        String userHash = Integer.toHexString(userId.hashCode()).substring(0, Math.min(6, Integer.toHexString(userId.hashCode()).length()));
        String indexName = "kb_" + userHash;  // e.g., "kb_a1b2c3" = 9 chars max
        
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
        
        return Mono.fromFuture(() -> asyncClient.createIndex(request))
                .doOnNext(response -> {
                    // Log response immediately when received
                    CreateIndexResponseBody body = response.getBody();
                    log.info("CreateIndex response received - StatusCode: {}, RequestId: {}, Code: {}, Message: {}, Success: {}, Status: {}",
                            response.getStatusCode(),
                            body != null ? body.getRequestId() : "null",
                            body != null ? body.getCode() : "null",
                            body != null ? body.getMessage() : "null",
                            body != null ? body.getSuccess() : "null",
                            body != null ? body.getStatus() : "null");
                })
                .flatMap(response -> {
                    CreateIndexResponseBody body = response.getBody();
                    
                    // Check HTTP status code first
                    if (response.getStatusCode() != 200) {
                        String errorMsg = body != null ? body.getMessage() : "Unknown error";
                        String errorCode = body != null ? body.getCode() : String.valueOf(response.getStatusCode());
                        log.error("Bailian API HTTP error - StatusCode: {}, Code: {}, Message: {}", 
                                response.getStatusCode(), errorCode, errorMsg);
                        return Mono.error(new RuntimeException("Bailian API error (HTTP " + response.getStatusCode() + "): " + errorCode + " - " + errorMsg));
                    }
                    
                    // Check for API error response
                    if (body != null && Boolean.FALSE.equals(body.getSuccess())) {
                        log.error("Bailian API error - Code: {}, Message: {}", body.getCode(), body.getMessage());
                        return Mono.error(new RuntimeException("Bailian API error: " + body.getCode() + " - " + body.getMessage()));
                    }
                    
                    if (body == null || body.getData() == null) {
                        log.error("Failed to create knowledge base, empty response. RequestId: {}", 
                                body != null ? body.getRequestId() : "null");
                        return Mono.error(new RuntimeException("Failed to create knowledge base: empty response"));
                    }
                    
                    String indexId = body.getData().getId();
                    if (indexId == null || indexId.isEmpty()) {
                        log.error("Failed to create knowledge base, no index ID returned. Message: {}", 
                                body.getMessage());
                        return Mono.error(new RuntimeException("Failed to create knowledge base: " + body.getMessage()));
                    }
                    
                    log.info("Created knowledge base with indexId: {} for user: {}", indexId, userId);
                    
                    UserKnowledgeBase kb = new UserKnowledgeBase(
                            UUID.randomUUID().toString(),
                            userId,
                            indexId,
                            bailianConfig.getWorkspaceId(),
                            indexName,
                            UserKnowledgeBase.STATUS_ACTIVE
                    );
                    
                    return userKnowledgeBaseRepository.save(kb);
                })
                .doOnSuccess(kb -> log.info("Saved knowledge base {} for user {}", kb.getIndexId(), userId))
                .doOnError(e -> log.error("Failed to create knowledge base for user {}: {}", userId, e.getMessage()));
    }

    /**
     * Add a note to user's knowledge base.
     * Complete flow:
     * 1. ApplyFileUploadLease - get upload credentials
     * 2. Upload to OSS using the credentials
     * 3. AddFile - register file in data center
     * 4. SubmitIndexAddDocumentsJob - add to knowledge base index
     */
    public Mono<NoteSyncHistory> addNoteToKnowledgeBase(Note note) {
        String noteId = note.getId();
        String userId = note.getUserId();
        
        log.info("Adding note {} to knowledge base for user {}", noteId, userId);
        
        AsyncClient asyncClient = getClient();
        if (asyncClient == null) {
            log.error("Bailian client not initialized for note sync");
            return createFailedSyncHistory(noteId, userId, "", "Bailian client not initialized");
        }
        
        // Build document content
        String documentContent = buildDocumentContent(note);
        byte[] contentBytes = documentContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String fileName = "note_" + noteId + ".txt";
        String md5 = calculateMd5(contentBytes);
        
        return getOrCreateKnowledgeBase(userId)
                .flatMap(kb -> {
                    log.info("Uploading note {} ({} bytes) to index {}", noteId, contentBytes.length, kb.getIndexId());
                    
                    // Step 1: Apply for upload lease
                    ApplyFileUploadLeaseRequest leaseRequest = ApplyFileUploadLeaseRequest.builder()
                            .workspaceId(bailianConfig.getWorkspaceId())
                            .categoryId(kb.getIndexId())  // Use index as category
                            .categoryType("UNSTRUCTURED")
                            .fileName(fileName)
                            .md5(md5)
                            .sizeInBytes(String.valueOf(contentBytes.length))
                            .build();
                    
                    return Mono.fromFuture(() -> asyncClient.applyFileUploadLease(leaseRequest))
                            .flatMap(leaseResponse -> {
                                ApplyFileUploadLeaseResponseBody body = leaseResponse.getBody();
                                
                                log.info("ApplyFileUploadLease response - Success: {}, Code: {}, Message: {}",
                                        body != null ? body.getSuccess() : null,
                                        body != null ? body.getCode() : null,
                                        body != null ? body.getMessage() : null);
                                
                                if (body == null || body.getData() == null) {
                                    return Mono.error(new RuntimeException("Failed to get upload lease: " + 
                                            (body != null ? body.getMessage() : "empty response")));
                                }
                                
                                ApplyFileUploadLeaseResponseBody.Data data = body.getData();
                                String leaseId = data.getFileUploadLeaseId();
                                ApplyFileUploadLeaseResponseBody.Param param = data.getParam();
                                
                                if (param == null || param.getUrl() == null) {
                                    return Mono.error(new RuntimeException("No upload URL in lease response"));
                                }
                                
                                log.info("Got upload lease: {}, URL: {}", leaseId, param.getUrl());
                                
                                // Step 2: Upload file to OSS
                                return uploadToOss(param.getUrl(), param.getMethod(), param.getHeaders(), contentBytes)
                                        .then(Mono.just(leaseId));
                            })
                            .flatMap(leaseId -> {
                                // Step 3: Register file with AddFile
                                AddFileRequest addFileRequest = AddFileRequest.builder()
                                        .workspaceId(bailianConfig.getWorkspaceId())
                                        .categoryId(kb.getIndexId())
                                        .leaseId(leaseId)
                                        .parser("DASHSCOPE_DOCMIND")
                                        .build();
                                
                                return Mono.fromFuture(() -> asyncClient.addFile(addFileRequest))
                                        .map(addFileResponse -> {
                                            AddFileResponseBody addBody = addFileResponse.getBody();
                                            log.info("AddFile response - Success: {}, FileId: {}",
                                                    addBody != null ? addBody.getSuccess() : null,
                                                    addBody != null && addBody.getData() != null ? addBody.getData().getFileId() : null);
                                            
                                            return addBody != null && addBody.getData() != null ? 
                                                    addBody.getData().getFileId() : null;
                                        });
                            })
                            .flatMap(fileId -> {
                                if (fileId == null) {
                                    return Mono.error(new RuntimeException("Failed to add file, no fileId returned"));
                                }
                                
                                // Step 4: Add document to index
                                SubmitIndexAddDocumentsJobRequest indexRequest = SubmitIndexAddDocumentsJobRequest.builder()
                                        .workspaceId(bailianConfig.getWorkspaceId())
                                        .indexId(kb.getIndexId())
                                        .sourceType("DATA_CENTER_FILE")
                                        .documentIds(Collections.singletonList(fileId))
                                        .build();
                                
                                return Mono.fromFuture(() -> asyncClient.submitIndexAddDocumentsJob(indexRequest))
                                        .map(indexResponse -> {
                                            SubmitIndexAddDocumentsJobResponseBody indexBody = indexResponse.getBody();
                                            log.info("SubmitIndexAddDocumentsJob response - Success: {}, JobId: {}",
                                                    indexBody != null ? indexBody.getSuccess() : null,
                                                    indexBody != null && indexBody.getData() != null ? indexBody.getData().getId() : null);
                                            return fileId;
                                        });
                            })
                            .flatMap(fileId -> {
                                // Create success sync record
                                NoteSyncHistory history = new NoteSyncHistory(
                                        UUID.randomUUID().toString(),
                                        noteId,
                                        userId,
                                        kb.getIndexId(),
                                        NoteSyncHistory.STATUS_SUCCESS
                                );
                                history.setDocumentId(fileId);
                                return noteSyncHistoryRepository.save(history);
                            });
                })
                .doOnSuccess(h -> log.info("Successfully synced note {} to knowledge base, fileId: {}", noteId, h.getDocumentId()))
                .onErrorResume(e -> {
                    log.error("Failed to add note {} to knowledge base: {}", noteId, e.getMessage(), e);
                    return createFailedSyncHistory(noteId, userId, "", e.getMessage());
                });
    }

    /**
     * Upload file content to OSS using the provided credentials.
     */
    private Mono<Void> uploadToOss(String url, String method, Object headersObj, byte[] content) {
        log.info("Uploading {} bytes to OSS: {}", content.length, url);
        
        org.springframework.web.reactive.function.client.WebClient webClient = 
                org.springframework.web.reactive.function.client.WebClient.builder().build();
        
        org.springframework.web.reactive.function.client.WebClient.RequestBodySpec requestSpec;
        
        if ("PUT".equalsIgnoreCase(method)) {
            requestSpec = webClient.put().uri(url);
        } else {
            requestSpec = webClient.post().uri(url);
        }
        
        // Add headers from the lease response
        if (headersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) headersObj;
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestSpec = requestSpec.header(entry.getKey(), entry.getValue());
            }
        }
        
        return requestSpec
                .bodyValue(content)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("OSS upload successful, status: {}", response.getStatusCode()))
                .doOnError(e -> log.error("OSS upload failed: {}", e.getMessage()))
                .then();
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
    public Mono<RetrieveResult> retrieve(String userId, String query) {
        log.debug("Retrieving from knowledge base for user {}, query length: {}", 
                userId, query.length());
        
        AsyncClient asyncClient = getClient();
        if (asyncClient == null) {
            log.warn("Bailian client not initialized, returning empty result");
            return Mono.just(new RetrieveResult(Collections.emptyList(), null));
        }
        
        return userKnowledgeBaseRepository.findByUserId(userId)
                .flatMap(kb -> {
                    log.info("Retrieving from index {} for user {}", kb.getIndexId(), userId);
                    
                    // Build retrieve request
                    RetrieveRequest request = RetrieveRequest.builder()
                            .workspaceId(bailianConfig.getWorkspaceId())
                            .indexId(kb.getIndexId())
                            .query(query)
                            .denseSimilarityTopK(bailianConfig.getRetrieveTopK())
                            .build();
                    
                    return Mono.fromFuture(() -> asyncClient.retrieve(request))
                            .map(this::parseRetrieveResponse);
                })
                .defaultIfEmpty(new RetrieveResult(Collections.emptyList(), null))
                .doOnSuccess(result -> log.info("Retrieved {} nodes from knowledge base for user {}", 
                        result.getNodes() != null ? result.getNodes().size() : 0, userId))
                .onErrorResume(e -> {
                    log.error("Failed to retrieve from knowledge base for user {}: {}", userId, e.getMessage());
                    return Mono.just(new RetrieveResult(Collections.emptyList(), null));
                });
    }

    /**
     * Check if a note has been synced to knowledge base.
     */
    public Mono<Boolean> isNoteSynced(String noteId) {
        return noteSyncHistoryRepository.isNoteSynced(noteId);
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
                        resultMetadata.setDocumentId(String.valueOf(metaMap.get("docId")));
                        resultMetadata.setDocumentName(String.valueOf(metaMap.get("docName")));
                        resultMetadata.setTitle(String.valueOf(metaMap.get("title")));
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
    private Mono<NoteSyncHistory> createFailedSyncHistory(String noteId, String userId, 
                                                           String indexId, String errorMessage) {
        NoteSyncHistory history = new NoteSyncHistory(
                UUID.randomUUID().toString(),
                noteId,
                userId,
                indexId,
                NoteSyncHistory.STATUS_FAILED
        );
        history.setErrorMessage(errorMessage);
        return noteSyncHistoryRepository.save(history);
    }
}
