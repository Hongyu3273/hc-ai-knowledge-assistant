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

/** Expose a named ChatClient without changing DashScope ChatModel auto-configuration. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.gemini", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfiguration {

    @Bean(destroyMethod = "close")
    public Client geminiApiClient(GeminiProperties properties) {
        Assert.hasText(properties.getApiKey(), "GEMINI_API_KEY or app.gemini.api-key must be configured when Gemini is enabled");
        Assert.isTrue(properties.getTimeoutMs() > 0, "app.gemini.timeout-ms must be greater than 0");
        return Client.builder()
                .vertexAI(false)
                .apiKey(properties.getApiKey())
                .httpOptions(HttpOptions.builder().timeout(properties.getTimeoutMs()).build())
                .build();
    }

    @Bean
    public ChatClient geminiChatClient(Client geminiApiClient) {
        // Keep the Google ChatModel inside this client instead of publishing it as a ChatModel bean.
        // LoveModelConfiguration registers the two Gemini routes explicitly.
        return ChatClient.builder(GoogleGenAiChatModel.builder()
                        .genAiClient(geminiApiClient)
                        .build())
                .build();
    }
}
