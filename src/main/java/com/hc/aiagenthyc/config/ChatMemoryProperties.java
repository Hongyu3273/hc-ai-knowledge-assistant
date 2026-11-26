package com.hc.aiagenthyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Configuration for the KnowledgeAssistant conversation-memory window ({@code app.chat-memory.*}).
 * The record has no built-in defaults; application.yml supplies {@code ${user.dir}/tmp/chat-memory} and 10.
 *
 * @param directory directory for local conversation files; must not be blank
 * @param maxMessages maximum number of messages retained per conversation; must be greater than 0
 */
@ConfigurationProperties(prefix = "app.chat-memory")
public record ChatMemoryProperties(String directory, int maxMessages) {

    /** Fails startup when the directory is blank or the window size is not positive. */
    public ChatMemoryProperties {
        Assert.hasText(directory, "app.chat-memory.directory cannot be empty");
        Assert.isTrue(maxMessages > 0, "app.chat-memory.max-messages must be greater than 0");
    }
}
