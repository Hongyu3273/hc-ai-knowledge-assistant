package com.hc.aiagenthyc.controller;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example endpoints using LangChain4j and DashScope.
 * Uses the QwenChatModel (ChatModel) supplied by auto-configuration.
 */
@RestController
@RequestMapping("/langchain4j")
public class LangChain4jChatController {

    private final ChatModel chatModel;

    public LangChain4jChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * GET /api/langchain4j/chat?message=Hello
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "Who are you?") String message) {
        return chatModel.chat(message);
    }
}
