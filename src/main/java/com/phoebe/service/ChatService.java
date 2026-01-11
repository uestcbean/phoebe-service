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
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final DashScopeConfig dashScopeConfig;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatService(DashScopeConfig dashScopeConfig, ObjectMapper objectMapper) {
        this.dashScopeConfig = dashScopeConfig;
        this.objectMapper = objectMapper;
    }

    public Flux<ServerSentEvent<String>> streamChat(ChatRequest request) {
        log.info("Starting stream chat with DashScope SDK, sessionId: {}, message length: {}",
                request.getSessionId(), request.getMessage().length());

        // 创建 Sink 用于桥接 RxJava Flowable 和 Reactor Flux
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 异步执行 DashScope 调用
        CompletableFuture.runAsync(() -> {
            try {
                executeStreamCall(request, sink);
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
                .messages(Collections.singletonList(userMessage))
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
