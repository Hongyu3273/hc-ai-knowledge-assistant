package com.hc.aiagenthyc.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;

/**
 * LangChain4j + DashScope（通义千问）调用示例。
 * 文档：https://docs.langchain4j.dev/integrations/language-models/dashscope/
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
            String result = callWithMessage("你是谁？");
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("LangChain4j DashScope 调用失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
