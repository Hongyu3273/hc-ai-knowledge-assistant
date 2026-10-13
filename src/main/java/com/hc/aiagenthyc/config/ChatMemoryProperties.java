package com.hc.aiagenthyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * LoveApp 聊天记忆配置。
 *
 * @param directory 本地文件存储目录
 * @param maxMessages 每个会话最多保留的消息数
 */
@ConfigurationProperties(prefix = "app.chat-memory")
public record ChatMemoryProperties(String directory, int maxMessages) {

    public ChatMemoryProperties {
        Assert.hasText(directory, "app.chat-memory.directory cannot be empty");
        Assert.isTrue(maxMessages > 0, "app.chat-memory.max-messages must be greater than 0");
    }
}
