package com.hc.aiagenthyc.controller;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LangChain4j DashScope 示例接口。
 * 依赖自动装配的 QwenChatModel（ChatModel）。
 */
@RestController
@RequestMapping("/langchain4j")
public class LangChain4jChatController {

    private final ChatModel chatModel;

    public LangChain4jChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * GET /api/langchain4j/chat?message=你是谁
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你是谁？") String message) {
        return chatModel.chat(message);
    }
}
