package com.hc.aiagenthyc.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring AI Alibaba + 通义千问 示例接口。
 * 文档：https://java2ai.com/docs/overview
 */
@RestController
@RequestMapping("/ai")
public class SpringAiChatController {

    private final ChatClient chatClient;

    public SpringAiChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("你是一个博学的智能聊天助手，请根据用户提问回答！")
                .build();
    }

    /**
     * 单轮对话：GET /api/ai/chat?message=你是谁
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你是谁？") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * 覆盖本次调用参数：GET /api/ai/chat/creative?message=写一首短诗
     */
    @GetMapping("/chat/creative")
    public String creativeChat(@RequestParam(defaultValue = "写一首关于春天的短诗") String message) {
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
