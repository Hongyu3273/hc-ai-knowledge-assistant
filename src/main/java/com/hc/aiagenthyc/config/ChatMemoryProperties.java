package com.hc.aiagenthyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Configuration for the LoveApp conversation-memory window.
 *
 * @param directory directory for local conversation files
 * @param maxMessages maximum number of messages retained per conversation
 */
@ConfigurationProperties(prefix = "app.chat-memory")
public record ChatMemoryProperties(String directory, int maxMessages) {

    public ChatMemoryProperties {
        Assert.hasText(directory, "app.chat-memory.directory cannot be empty");
        Assert.isTrue(maxMessages > 0, "app.chat-memory.max-messages must be greater than 0");
    }
}
