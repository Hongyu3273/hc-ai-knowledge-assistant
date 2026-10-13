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

/** 将供应商客户端集中注册，业务层只依赖模型标识。 */
@Configuration(proxyBeanMethods = false)
public class LoveModelConfiguration {
    @Bean("loveAppModelClients")
    public Map<String, ModelRoute> loveAppModelClients(
            Map<String, ChatModel> models,
            @Qualifier("geminiChatClient") ObjectProvider<ChatClient> gemini,
            ObjectProvider<GeminiProperties> geminiProperties) {
        Map<String, ModelRoute> clients = new LinkedHashMap<>();
        models.forEach((name, model) -> {
            String id = switch (name) {
                case "dashScopeChatModel" -> "dashscope";
                case "ollamaChatModel" -> "ollama";
                default -> name;
            };
            clients.put(id, new ModelRoute(ChatClient.create(model), null));
        });
        gemini.ifAvailable(client -> {
            GeminiProperties properties = geminiProperties.getObject();
            Assert.hasText(properties.getProModel(), "Gemini Pro 模型名称不能为空");
            Assert.hasText(properties.getFlashLiteModel(), "Gemini Flash-Lite 模型名称不能为空");
            clients.put("gemini-2.5-pro", new ModelRoute(client, properties.getProModel(), properties.getProInputs()));
            clients.put("gemini-2.5-flash-lite", new ModelRoute(client, properties.getFlashLiteModel(), properties.getFlashLiteInputs()));
        });
        return Map.copyOf(clients);
    }
}
