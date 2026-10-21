package com.hc.aiagenthyc.demo.invoke;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Example of calling Qwen through Spring AI Alibaba with a Spring-managed ChatModel.
 * For HTTP endpoints, see {@link com.hc.aiagenthyc.controller.SpringAiChatController}.
 */
public final class SpringAiInvoke {

    private SpringAiInvoke() {
    }

    public static String callWithMessage(ChatModel chatModel, String userMessage) {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem("You are a helpful assistant.")
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .build())
                .build();

        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
}
