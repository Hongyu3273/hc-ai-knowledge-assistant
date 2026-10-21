package com.hc.aiagenthyc.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example endpoints using Spring AI Alibaba and DashScope Qwen.
 * Documentation: https://java2ai.com/docs/overview
 */
@RestController
@RequestMapping("/ai")
public class SpringAiChatController {

    private final ChatClient chatClient;

    public SpringAiChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("You are a knowledgeable AI chat assistant. Answer the user's questions.")
                .build();
    }

    /**
     * Single-turn chat: GET /api/ai/chat?message=Hello
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "Who are you?") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * Override options for one call: GET /api/ai/chat/creative?message=Write+a+short+poem
     */
    @GetMapping("/chat/creative")
    public String creativeChat(@RequestParam(defaultValue = "Write a short poem about spring") String message) {
        return chatClient.prompt()
                .user(message)
                .options(DashScopeChatOptions.builder()
                        .temperature(1.0)
                        .model("qwen-plus")
                        .build())
                .call()
                .content();
    }
}
