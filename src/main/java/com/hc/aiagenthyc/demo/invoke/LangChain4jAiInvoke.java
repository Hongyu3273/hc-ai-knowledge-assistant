package com.hc.aiagenthyc.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;

/**
 * Example of calling DashScope Qwen through LangChain4j.
 * Documentation: https://docs.langchain4j.dev/integrations/language-models/dashscope/
 */
public class LangChain4jAiInvoke {

    public static String callWithMessage(String userMessage) {
        ChatModel chatModel = QwenChatModel.builder()
                .apiKey(TestApiKey.API_KEY)
                .modelName("qwen-plus")
                .build();
        return chatModel.chat(userMessage);
    }

    public static void main(String[] args) {
        try {
            String result = callWithMessage("Who are you?");
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("LangChain4j DashScope call failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
