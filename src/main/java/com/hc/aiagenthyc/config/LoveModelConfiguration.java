package com.hc.aiagenthyc.config;

import com.hc.aiagenthyc.model.ModelRoute;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registers provider clients centrally so business code can select models by route ID. */
@Configuration(proxyBeanMethods = false)
public class LoveModelConfiguration {
    @Bean("loveAppModelClients")
    public Map<String, ModelRoute> loveAppModelClients(
            // Spring supplies all registered ChatModel beans, keyed by their bean names.
            Map<String, ChatModel> models,
            @Qualifier("geminiChatClient") ObjectProvider<ChatClient> gemini,
            ObjectProvider<GeminiProperties> geminiProperties) {
        Map<String, ModelRoute> clients = new LinkedHashMap<>();
        // Give known providers stable API route IDs. An absent ChatModel creates no route.
        models.forEach((name, model) -> {
            String id = switch (name) {
                case "dashScopeChatModel" -> "dashscope";
                case "ollamaChatModel" -> "ollama";
                default -> name;
            };
            clients.put(id, new ModelRoute(ChatClient.create(model), null));
        });
        // ObjectProvider keeps Gemini optional when app.gemini.enabled is false.
        // Both Gemini routes share a client but override the provider model for each request.
        gemini.ifAvailable(client -> {
            GeminiProperties properties = geminiProperties.getObject();
            Assert.hasText(properties.getProModel(), "The Gemini Pro model name must not be empty");
            Assert.hasText(properties.getFlashLiteModel(), "The Gemini Flash-Lite model name must not be empty");
            clients.put("gemini-2.5-pro", new ModelRoute(client, properties.getProModel(), properties.getProInputs()));
            clients.put("gemini-2.5-flash-lite", new ModelRoute(client, properties.getFlashLiteModel(), properties.getFlashLiteInputs()));
        });
        return Map.copyOf(clients);
    }
}
