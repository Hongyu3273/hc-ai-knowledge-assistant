package com.hc.aiagenthyc.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/** 只暴露命名 ChatClient，避免干扰 DashScope 的默认 ChatModel 自动装配。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.gemini", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfiguration {

    @Bean(destroyMethod = "close")
    public Client geminiApiClient(GeminiProperties properties) {
        Assert.hasText(properties.getApiKey(), "启用 Gemini 时必须配置 GEMINI_API_KEY 或 app.gemini.api-key");
        Assert.isTrue(properties.getTimeoutMs() > 0, "app.gemini.timeout-ms 必须大于 0");
        return Client.builder()
                .vertexAI(false)
                .apiKey(properties.getApiKey())
                .httpOptions(HttpOptions.builder().timeout(properties.getTimeoutMs()).build())
                .build();
    }

    @Bean
    public ChatClient geminiChatClient(Client geminiApiClient) {
        return ChatClient.builder(GoogleGenAiChatModel.builder()
                        .genAiClient(geminiApiClient)
                        .build())
                .build();
    }
}
