package com.hc.aiagenthyc.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

/**
 * 记录用户最新一条消息和 AI 最终回复的日志 Advisor。
 *
 * <p>{@code nextCall}/{@code nextStream} 之前相当于 before，响应返回之后相当于 after。</p>
 */
public final class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(MyLoggerAdvisor.class);

    private final int order;

    public MyLoggerAdvisor() {
        this(0);
    }

    public MyLoggerAdvisor(int order) {
        this.order = order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        logRequest(request);

        ChatClientResponse response = chain.nextCall(request);

        logResponse(response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        logRequest(request);

        Flux<ChatClientResponse> responses = chain.nextStream(request);

        return new ChatClientMessageAggregator()
                .aggregateChatClientResponse(responses, this::logResponse);
    }

    private void logRequest(ChatClientRequest request) {
        String userText = request.prompt().getUserMessage().getText();
        log.info("用户请求：{}", userText);
    }

    private void logResponse(ChatClientResponse response) {
        log.info("AI 响应：{}", extractResponseText(response));
    }

    private String extractResponseText(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return "";
        }

        Generation result = chatResponse.getResult();
        if (result == null || result.getOutput() == null || result.getOutput().getText() == null) {
            return "";
        }

        return result.getOutput().getText();
    }

    @Override
    public String getName() {
        return MyLoggerAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }
}
