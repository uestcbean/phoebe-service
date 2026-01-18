package com.phoebe.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoebe.config.DashScopeConfig;
import com.phoebe.dto.ChatRequest;
import com.phoebe.dto.bailian.RetrieveResult;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final DashScopeConfig dashScopeConfig;
    private final BailianKnowledgeService bailianKnowledgeService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * System prompt template for RAG with multimodal support.
     */
    private static final String RAG_MULTIMODAL_SYSTEM_PROMPT = """
            你是一个智能助手，专门帮助用户回顾和理解他们的学习笔记。
            
            以下是从用户知识库中检索到的相关笔记内容，请参考这些信息来回答问题：
            
            {context}
            
            回答要求：
            1. 结合用户的输入（可能包含图片、音频或文件）和上述知识库内容来回答
            2. 如果知识库中有相关内容，优先基于笔记来回答
            3. 回答要清晰、有条理，支持使用 Markdown 格式（代码块、列表、表格等）
            """;
    
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个智能助手。请尽你所能帮助用户回答问题。
            回答支持使用 Markdown 格式，包括：
            - 代码块（使用 ```language 语法）
            - 列表（有序和无序）
            - 表格
            - 粗体、斜体等文本格式
            """;

    /**
     * Topic-specific prompt enhancements.
     * These prompts guide the LLM to focus on the selected topic area.
     */
    private static final Map<String, String> TOPIC_PROMPTS = Map.ofEntries(
            Map.entry("技术", """
                    
                    【当前对话主题：技术】
                    用户希望在技术领域进行探讨。请侧重于：
                    - 编程、软件开发、系统设计相关话题
                    - 提供代码示例时使用正确的语法高亮
                    - 解释技术概念时要准确且易于理解
                    """),
            Map.entry("学习", """
                    
                    【当前对话主题：学习】
                    用户希望专注于学习相关话题。请侧重于：
                    - 帮助用户理解和记忆知识点
                    - 提供学习方法和建议
                    - 总结要点，便于复习
                    """),
            Map.entry("日常", """
                    
                    【当前对话主题：日常】
                    用户想要进行日常闲聊。请侧重于：
                    - 轻松自然的对话风格
                    - 生活、健康、娱乐等话题
                    - 像朋友一样交流
                    """),
            Map.entry("创作", """
                    
                    【当前对话主题：创作】
                    用户希望进行创意写作。请侧重于：
                    - 提供创意灵感和建议
                    - 帮助完善文案、故事、文章
                    - 支持多种创作风格
                    """),
            Map.entry("工作", """
                    
                    【当前对话主题：工作】
                    用户希望讨论工作相关话题。请侧重于：
                    - 专业、高效的沟通风格
                    - 项目管理、职业发展建议
                    - 商务文档、邮件写作帮助
                    """),
            Map.entry("思考", """
                    
                    【当前对话主题：深度思考】
                    用户希望进行深度思考和分析。请侧重于：
                    - 提供多角度的分析
                    - 逻辑严谨的推理
                    - 哲学、心理、人生话题的探讨
                    """)
    );

    private static final String FILE_CONTENT_TEMPLATE = """
            
            用户上传的文件内容：
            --- {fileName} ---
            {content}
            --- 文件结束 ---
            """;

    public ChatService(DashScopeConfig dashScopeConfig, 
                       BailianKnowledgeService bailianKnowledgeService,
                       ObjectMapper objectMapper) {
        this.dashScopeConfig = dashScopeConfig;
        this.bailianKnowledgeService = bailianKnowledgeService;
        this.objectMapper = objectMapper;
    }

    public Flux<ServerSentEvent<String>> streamChat(Long userId, ChatRequest request) {
        log.info("Starting stream chat, sessionId: {}, userId: {}, inputType: {}, RAG: {}",
                request.getSessionId(), userId, request.getInputType(), request.isEnableRag());

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: Always do RAG retrieval first (regardless of input type)
                RetrieveResult retrieveResult = null;
                if (request.isEnableRag() && userId != null) {
                    log.info("Retrieving knowledge base context for user: {}", userId);
                    try {
                        retrieveResult = bailianKnowledgeService.retrieve(userId, request.getMessage());
                    } catch (Exception e) {
                        log.warn("RAG retrieval failed, continuing without context: {}", e.getMessage());
                    }
                }
                
                int nodeCount = retrieveResult != null && retrieveResult.getNodes() != null 
                        ? retrieveResult.getNodes().size() : 0;
                log.info("Retrieved {} knowledge nodes", nodeCount);

                // Step 2: Route to appropriate model based on input type
                if (request.hasImage()) {
                    sink.tryEmitNext(buildRetrievalEventWithRag(retrieveResult, "image"));
                    executeVisionStreamCall(request, retrieveResult, sink);
                } else if (request.hasAudio()) {
                    sink.tryEmitNext(buildRetrievalEventWithRag(retrieveResult, "audio"));
                    executeAudioStreamCall(request, retrieveResult, sink);
                } else if (request.hasFile()) {
                    sink.tryEmitNext(buildRetrievalEventWithRag(retrieveResult, "file"));
                    executeFileStreamCall(request, retrieveResult, sink);
                } else {
                    sink.tryEmitNext(buildRetrievalEventWithRag(retrieveResult, "text"));
                    executeTextStreamCall(request, retrieveResult, sink);
                }
            } catch (Exception e) {
                log.error("Error during stream chat", e);
                sink.tryEmitNext(buildErrorEvent(e.getMessage()));
                sink.tryEmitComplete();
            }
        }, executor);

        return sink.asFlux()
                .doOnCancel(() -> log.info("Stream chat cancelled"))
                .doOnComplete(() -> log.info("Stream chat completed"));
    }

    /**
     * Build system prompt with RAG context and topic guidance.
     */
    private String buildSystemPromptWithRag(RetrieveResult retrieveResult, String additionalContext, String topic) {
        StringBuilder promptBuilder = new StringBuilder();
        
        String contextString = "";
        if (retrieveResult != null) {
            contextString = retrieveResult.toContextString();
        }
        
        if (contextString != null && !contextString.isEmpty()) {
            promptBuilder.append(RAG_MULTIMODAL_SYSTEM_PROMPT.replace("{context}", contextString));
        } else {
            promptBuilder.append(DEFAULT_SYSTEM_PROMPT);
        }
        
        // Add topic-specific guidance if topic is provided
        if (topic != null && !topic.isBlank()) {
            String topicPrompt = TOPIC_PROMPTS.get(topic);
            if (topicPrompt != null) {
                promptBuilder.append(topicPrompt);
            } else {
                // Custom topic not in predefined list
                promptBuilder.append("\n\n【当前对话主题：").append(topic).append("】\n");
                promptBuilder.append("请围绕【").append(topic).append("】这个主题来回答问题，确保回答与该主题相关。\n");
            }
        }
        
        // Add additional context (e.g., for vision/audio prompts)
        if (additionalContext != null && !additionalContext.isEmpty()) {
            promptBuilder.append("\n").append(additionalContext);
        }
        
        return promptBuilder.toString();
    }

    /**
     * Execute vision model call with RAG context.
     */
    private void executeVisionStreamCall(ChatRequest request, RetrieveResult retrieveResult,
                                         Sinks.Many<ServerSentEvent<String>> sink)
            throws NoApiKeyException, InputRequiredException, UploadFileException {

        log.info("Using vision model: {}", dashScopeConfig.getVisionModel());

        MultiModalConversation conversation = new MultiModalConversation();
        
        // Build system prompt with RAG context and topic
        String systemPrompt = buildSystemPromptWithRag(retrieveResult, "请分析用户提供的图片，结合知识库内容回答问题。", request.getTopic());
        
        List<MultiModalMessage> messages = new ArrayList<>();
        
        // System message
        List<Map<String, Object>> systemContent = new ArrayList<>();
        systemContent.add(Map.of("text", systemPrompt));
        messages.add(MultiModalMessage.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemContent)
                .build());
        
        // User message with image
        List<Map<String, Object>> userContent = new ArrayList<>();
        String imageUrl = "data:" + (request.getImageMimeType() != null ? request.getImageMimeType() : "image/png") 
                + ";base64," + request.getImageBase64();
        userContent.add(Map.of("image", imageUrl));
        userContent.add(Map.of("text", request.getMessage()));

        messages.add(MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(userContent)
                .build());

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(dashScopeConfig.getKey())
                .model(dashScopeConfig.getVisionModel())
                .messages(messages)
                .incrementalOutput(true)
                .build();

        Flowable<MultiModalConversationResult> flowable = conversation.streamCall(param);

        flowable.blockingForEach(result -> processMultiModalResult(result, sink));

        sink.tryEmitNext(buildDoneEvent(null));
        sink.tryEmitComplete();
    }

    /**
     * Execute audio model call with RAG context.
     */
    private void executeAudioStreamCall(ChatRequest request, RetrieveResult retrieveResult,
                                        Sinks.Many<ServerSentEvent<String>> sink)
            throws NoApiKeyException, InputRequiredException, UploadFileException {

        log.info("Using audio model: {}", dashScopeConfig.getAudioModel());

        MultiModalConversation conversation = new MultiModalConversation();
        
        String systemPrompt = buildSystemPromptWithRag(retrieveResult, "请理解用户提供的语音内容，结合知识库内容回答问题。", request.getTopic());
        
        List<MultiModalMessage> messages = new ArrayList<>();
        
        // System message
        List<Map<String, Object>> systemContent = new ArrayList<>();
        systemContent.add(Map.of("text", systemPrompt));
        messages.add(MultiModalMessage.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemContent)
                .build());
        
        // User message with audio
        List<Map<String, Object>> userContent = new ArrayList<>();
        String audioFormat = request.getAudioFormat() != null ? request.getAudioFormat() : "wav";
        String audioUrl = "data:audio/" + audioFormat + ";base64," + request.getAudioBase64();
        userContent.add(Map.of("audio", audioUrl));
        userContent.add(Map.of("text", request.getMessage()));

        messages.add(MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(userContent)
                .build());

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(dashScopeConfig.getKey())
                .model(dashScopeConfig.getAudioModel())
                .messages(messages)
                .incrementalOutput(true)
                .build();

        Flowable<MultiModalConversationResult> flowable = conversation.streamCall(param);

        flowable.blockingForEach(result -> processMultiModalResult(result, sink));

        sink.tryEmitNext(buildDoneEvent(null));
        sink.tryEmitComplete();
    }

    /**
     * Execute text model call with file content and RAG context.
     */
    private void executeFileStreamCall(ChatRequest request, RetrieveResult retrieveResult,
                                       Sinks.Many<ServerSentEvent<String>> sink)
            throws NoApiKeyException, InputRequiredException {

        log.info("Processing file: {}", request.getFileName());

        Generation generation = new Generation();
        List<Message> messages = new ArrayList<>();

        // Build system prompt with RAG context and file content
        String fileContext = FILE_CONTENT_TEMPLATE
                .replace("{fileName}", request.getFileName() != null ? request.getFileName() : "未知文件")
                .replace("{content}", request.getFileContent());
        String systemPrompt = buildSystemPromptWithRag(retrieveResult, fileContext, request.getTopic());
        
        messages.add(Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemPrompt)
                .build());

        // Add history
        addHistoryMessages(request, messages);

        // Add current user message
        messages.add(Message.builder()
                .role(Role.USER.getValue())
                .content(request.getMessage())
                .build());

        GenerationParam param = GenerationParam.builder()
                .apiKey(dashScopeConfig.getKey())
                .model(dashScopeConfig.getModel())
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true)
                .build();

        Flowable<GenerationResult> flowable = generation.streamCall(param);

        flowable.blockingForEach(result -> processGenerationResult(result, sink));

        sink.tryEmitNext(buildDoneEvent(null));
        sink.tryEmitComplete();
    }

    /**
     * Execute standard text processing with RAG.
     */
    private void executeTextStreamCall(ChatRequest request, RetrieveResult retrieveResult,
                                       Sinks.Many<ServerSentEvent<String>> sink)
            throws NoApiKeyException, InputRequiredException {

        Generation generation = new Generation();
        List<Message> messages = new ArrayList<>();

        String systemPrompt = buildSystemPromptWithRag(retrieveResult, null, request.getTopic());
        messages.add(Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemPrompt)
                .build());

        // Add history
        addHistoryMessages(request, messages);

        // Add current user message
        messages.add(Message.builder()
                .role(Role.USER.getValue())
                .content(request.getMessage())
                .build());

        log.info("Sending {} messages to LLM, topic: {}", messages.size(), request.getTopic());

        GenerationParam param = GenerationParam.builder()
                .apiKey(dashScopeConfig.getKey())
                .model(dashScopeConfig.getModel())
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true)
                .build();

        Flowable<GenerationResult> flowable = generation.streamCall(param);

        flowable.blockingForEach(result -> processGenerationResult(result, sink));

        sink.tryEmitNext(buildDoneEvent(null));
        sink.tryEmitComplete();
    }

    /**
     * Add history messages (text only).
     */
    private void addHistoryMessages(ChatRequest request, List<Message> messages) {
        if (request.getHistory() == null || request.getHistory().isEmpty()) {
            return;
        }

        for (ChatRequest.ChatMessage historyMsg : request.getHistory()) {
            if (historyMsg.getContent() == null || historyMsg.getContent().isBlank()) {
                continue;
            }
            
            String role;
            if ("user".equalsIgnoreCase(historyMsg.getRole())) {
                role = Role.USER.getValue();
            } else if ("assistant".equalsIgnoreCase(historyMsg.getRole())) {
                role = Role.ASSISTANT.getValue();
            } else {
                continue;
            }

            messages.add(Message.builder()
                    .role(role)
                    .content(historyMsg.getContent())
                    .build());
        }
    }

    private void processMultiModalResult(MultiModalConversationResult result, 
                                         Sinks.Many<ServerSentEvent<String>> sink) {
        try {
            if (result.getOutput() != null && result.getOutput().getChoices() != null) {
                for (var choice : result.getOutput().getChoices()) {
                    if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                        List<Map<String, Object>> content = choice.getMessage().getContent();
                        for (Map<String, Object> item : content) {
                            if (item.containsKey("text")) {
                                String text = (String) item.get("text");
                                if (text != null && !text.isEmpty()) {
                                    sink.tryEmitNext(buildTokenEvent(text));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing multimodal result", e);
        }
    }

    private void processGenerationResult(GenerationResult result, Sinks.Many<ServerSentEvent<String>> sink) {
        if (result.getOutput() != null && result.getOutput().getChoices() != null) {
            for (var choice : result.getOutput().getChoices()) {
                if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                    String content = choice.getMessage().getContent();
                    if (!content.isEmpty()) {
                        sink.tryEmitNext(buildTokenEvent(content));
                    }
                }

                if ("stop".equals(choice.getFinishReason()) && result.getUsage() != null) {
                    sink.tryEmitNext(buildDoneEvent(result.getUsage()));
                }
            }
        }
    }

    private ServerSentEvent<String> buildTokenEvent(String delta) {
        try {
            String data = objectMapper.writeValueAsString(Map.of("delta", delta));
            return ServerSentEvent.<String>builder()
                    .event("token")
                    .data(data)
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                    .event("token")
                    .data("{\"delta\":\"\"}")
                    .build();
        }
    }

    private ServerSentEvent<String> buildDoneEvent(Object usage) {
        try {
            Map<String, Object> data = new HashMap<>();
            if (usage != null) data.put("usage", usage);
            String json = objectMapper.writeValueAsString(data);
            return ServerSentEvent.<String>builder()
                    .event("done")
                    .data(json)
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                    .event("done")
                    .data("{}")
                    .build();
        }
    }

    private ServerSentEvent<String> buildErrorEvent(String errorMessage) {
        try {
            String data = objectMapper.writeValueAsString(Map.of("error", errorMessage));
            return ServerSentEvent.<String>builder()
                    .event("error")
                    .data(data)
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                    .event("error")
                    .data("{\"error\":\"Unknown error\"}")
                    .build();
        }
    }

    /**
     * Build retrieval event with RAG results and input type.
     */
    private ServerSentEvent<String> buildRetrievalEventWithRag(RetrieveResult retrieveResult, String inputType) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("inputType", inputType);
            
            if (retrieveResult != null && retrieveResult.getNodes() != null && !retrieveResult.getNodes().isEmpty()) {
                data.put("ragEnabled", true);
                data.put("nodeCount", retrieveResult.getNodes().size());
                
                List<Map<String, Object>> references = new ArrayList<>();
                for (int i = 0; i < retrieveResult.getNodes().size(); i++) {
                    RetrieveResult.RetrieveNode node = retrieveResult.getNodes().get(i);
                    Map<String, Object> ref = new HashMap<>();
                    ref.put("index", i + 1);
                    ref.put("score", node.getScore());
                    String text = node.getText();
                    if (text != null && text.length() > 100) {
                        text = text.substring(0, 100) + "...";
                    }
                    ref.put("preview", text);
                    if (node.getMetadata() != null) {
                        ref.put("title", node.getMetadata().getTitle());
                    }
                    references.add(ref);
                }
                data.put("references", references);
            } else {
                data.put("ragEnabled", false);
                data.put("nodeCount", 0);
            }
            
            String json = objectMapper.writeValueAsString(data);
            return ServerSentEvent.<String>builder()
                    .event("retrieval")
                    .data(json)
                    .build();
        } catch (JsonProcessingException e) {
            return ServerSentEvent.<String>builder()
                    .event("retrieval")
                    .data("{\"ragEnabled\":false,\"nodeCount\":0}")
                    .build();
        }
    }
}
