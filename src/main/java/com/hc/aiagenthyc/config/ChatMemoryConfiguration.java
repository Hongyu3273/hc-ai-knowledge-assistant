package com.hc.aiagenthyc.config;

import com.hc.aiagenthyc.memory.FileBasedChatMemoryRepository;
import com.hc.aiagenthyc.memory.SynchronizedChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration for the chat-memory beans used by LoveApp.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatMemoryProperties.class)
public class ChatMemoryConfiguration {

    public static final String REPOSITORY_BEAN_NAME = "loveAppChatMemoryRepository";

    public static final String CHAT_MEMORY_BEAN_NAME = "loveAppChatMemory";

    @Bean(REPOSITORY_BEAN_NAME)
    public ChatMemoryRepository chatMemoryRepository(ChatMemoryProperties properties) {
        return new FileBasedChatMemoryRepository(properties.directory());
    }

    @Bean(CHAT_MEMORY_BEAN_NAME)
    public ChatMemory chatMemory(
            @Qualifier(REPOSITORY_BEAN_NAME) ChatMemoryRepository repository,
            ChatMemoryProperties properties) {
        // This is a bounded context window, not a permanent transcript of every conversation turn.
        ChatMemory windowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.maxMessages())
                .build();
        // Protect individual memory operations; ConversationExecutor protects the full read-call-save sequence.
        return new SynchronizedChatMemory(windowChatMemory);
    }
}
