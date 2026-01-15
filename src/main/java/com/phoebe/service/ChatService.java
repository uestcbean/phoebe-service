package com.phoebe.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * System prompt template for RAG.
     * The {context} placeholder will be replaced with retrieved knowledge.
     */
    private static final String RAG_SYSTEM_PROMPT = """
            你是一个智能助手。请根据用户的问题和以下参考信息来回答。
            如果参考信息中包含相关内容，请优先基于这些信息回答。
            如果参考信息与问题无关或信息不足，请基于你的知识回答，但要说明这不是来自用户的学习记录。
            
            {context}
            """;

    public ChatService(DashScopeConfig dashScopeConfig, 
                       BailianKnowledgeService bailianKnowledgeService,
                       ObjectMapper objectMapper) {
        this.dashScopeConfig = dashScopeConfig;
        this.bailianKnowledgeService = bailianKnowledgeService;
        this.objectMapper = objectMapper;
    }

    public Flux<ServerSentEvent<String>> streamChat(ChatRequest request) {
        log.info("Starting stream chat with DashScope SDK, sessionId: {}, userId: {}, message length: {}, RAG: {}",
                request.getSessionId(), request.getUserId(), request.getMessage().length(), request.isEnableRag());

        // 创建 Sink 用于桥接 RxJava Flowable 和 Reactor Flux
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        // If RAG is enabled, first retrieve knowledge, then call LLM
        if (request.isEnableRag() && request.getUserId() != null) {
            // Retrieve knowledge base context first
            bailianKnowledgeService.retrieve(request.getUserId(), request.getMessage())
                    .defaultIfEmpty(new RetrieveResult())
                    .subscribe(
                            retrieveResult -> {
                                // Execute LLM call with retrieved context
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        executeStreamCallWithContext(request, retrieveResult, sink);
                                    } catch (Exception e) {
                                        log.error("Error during stream chat with RAG", e);
                                        sink.tryEmitNext(buildErrorEvent(e.getMessage()));
                                        sink.tryEmitComplete();
                                    }
                                }, executor);
                            },
                            error -> {
                                log.error("Error retrieving from knowledge base, falling back to no-RAG", error);
                                // Fallback: execute without RAG context
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        executeStreamCall(request, sink);
                                    } catch (Exception e) {
                                        log.error("Error during stream chat", e);
                                        sink.tryEmitNext(buildErrorEvent(e.getMessage()));
                                        sink.tryEmitComplete();
                                    }
                                }, executor);
                            }
                    );
        } else {
            // No RAG, directly call LLM
            CompletableFuture.runAsync(() -> {
                try {
                    executeStreamCall(request, sink);
                } catch (Exception e) {
                    log.error("Error during stream chat", e);
                    sink.tryEmitNext(buildErrorEvent(e.getMessage()));
                    sink.tryEmitComplete();
                }
            }, executor);
        }

        return sink.asFlux()
                .doOnCancel(() -> log.info("Stream chat cancelled"))
                .doOnComplete(() -> log.info("Stream chat completed"));
    }

    /**
     * Execute stream call with RAG context from knowledge base.
     */
    private void executeStreamCallWithContext(ChatRequest request, 
                                              RetrieveResult retrieveResult,
                                              Sinks.Many<ServerSentEvent<String>> sink)
            throws NoApiKeyException, InputRequiredException {

        Generation generation = new Generation();
        List<Message> messages = new ArrayList<>();

        // Build system message with retrieved context
        String contextString = retrieveResult.toContextString();
        if (contextString != null && !contextString.isEmpty()) {
            String systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", contextString);
            Message systemMessage = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(systemPrompt)
                    .build();
            messages.add(systemMessage);
            log.debug("Added RAG context with {} nodes to system prompt", 
                    retrieveResult.getNodes() != null ? retrieveResult.getNodes().size() : 0);
        }

        // 构建用户消息
        Message userMessage = Message.builder()
                .role(Role.USER.getValue())
                .content(request.getMessage())
                .build();
        messages.add(userMessage);

        // 构建请求参数
        GenerationParam param = GenerationParam.builder()
                .apiKey(dashScopeConfig.getKey())
                .model(dashScopeConfig.getModel())
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true)  // 增量输出
                .build();

        // 调用流式接口
        Flowable<GenerationResult> flowable = generation.streamCall(param);

        // 订阅并处理流式结果
        flowable.blockingForEach(result -> {
            try {
                processGenerationResult(result, sink);
            } catch (Exception e) {
                log.error("Error processing generation result", e);
            }
        });

        // 发送完成事件
        sink.tryEmitNext(buildDoneEvent(null));
        sink.tryEmitComplete();
    }

    /**
     * Execute stream call without RAG context (original implementation).
     */
    private void executeStreamCall(ChatRequest request, Sinks.Many<ServerSentEvent<String>> sink)
            throws NoApiKeyException, InputRequiredException {

        Generation generation = new Generation();

        // 构建用户消息
        Message userMessage = Message.builder()
                .role(Role.USER.getValue())
                .content(request.getMessage())
                .build();

        // 构建请求参数
        GenerationParam param = GenerationParam.builder()
                .apiKey(dashScopeConfig.getKey())
                .model(dashScopeConfig.getModel())
                .messages(List.of(userMessage))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true)  // 增量输出
                .build();

        // 调用流式接口
        Flowable<GenerationResult> flowable = generation.streamCall(param);

        // 订阅并处理流式结果
        flowable.blockingForEach(result -> {
            try {
                processGenerationResult(result, sink);
            } catch (Exception e) {
                log.error("Error processing generation result", e);
            }
        });

        // 发送完成事件
        sink.tryEmitNext(buildDoneEvent(null));
        sink.tryEmitComplete();
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

                // 检查是否结束
                if ("stop".equals(choice.getFinishReason())) {
                    // 发送 usage 信息
                    if (result.getUsage() != null) {
                        sink.tryEmitNext(buildDoneEvent(result.getUsage()));
                    }
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
            log.error("Failed to serialize delta", e);
            return ServerSentEvent.<String>builder()
                    .event("token")
                    .data("{\"delta\":\"\"}")
                    .build();
        }
    }

    private ServerSentEvent<String> buildDoneEvent(Object usage) {
        try {
            Map<String, Object> data = new HashMap<>();
            if (usage != null) {
                data.put("usage", usage);
            }
            String json = objectMapper.writeValueAsString(data);
            return ServerSentEvent.<String>builder()
                    .event("done")
                    .data(json)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize done event", e);
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
}
