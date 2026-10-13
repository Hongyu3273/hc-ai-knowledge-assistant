package com.hc.aiagenthyc.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBasedChatMemoryRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void persistsListsAndDeletesConversations() {
        FileBasedChatMemoryRepository repository =
                new FileBasedChatMemoryRepository(this.tempDirectory.toString());
        List<Message> expected = List.of(
                new UserMessage("你好"),
                new AssistantMessage("你好，有什么可以帮你？"));

        repository.saveAll("conversation-1", expected);

        List<Message> actual = repository.findByConversationId("conversation-1");
        assertEquals(2, actual.size());
        assertInstanceOf(UserMessage.class, actual.get(0));
        assertInstanceOf(AssistantMessage.class, actual.get(1));
        assertEquals("你好", actual.get(0).getText());
        assertEquals("你好，有什么可以帮你？", actual.get(1).getText());
        assertEquals(List.of("conversation-1"), repository.findConversationIds());

        repository.deleteByConversationId("conversation-1");

        assertTrue(repository.findByConversationId("conversation-1").isEmpty());
        assertTrue(repository.findConversationIds().isEmpty());
    }

    @Test
    void keepsConversationFilesInsideTheConfiguredDirectory() throws Exception {
        FileBasedChatMemoryRepository repository =
                new FileBasedChatMemoryRepository(this.tempDirectory.toString());
        Path escapedFile = this.tempDirectory.getParent().resolve("escaped.kryo");

        repository.saveAll("../escaped", List.of(new UserMessage("安全测试")));

        assertFalse(Files.exists(escapedFile));
        assertEquals(List.of("../escaped"), repository.findConversationIds());
        try (var files = Files.list(this.tempDirectory)) {
            assertEquals(1, files.filter(path -> path.getFileName().toString().endsWith(".kryo")).count());
        }
    }

    @Test
    void messageWindowKeepsOnlyTheLatestMessages() {
        FileBasedChatMemoryRepository repository =
                new FileBasedChatMemoryRepository(this.tempDirectory.toString());
        ChatMemory memory = new SynchronizedChatMemory(MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(3)
                .build());

        for (int index = 0; index < 5; index++) {
            memory.add("window", new UserMessage("message-" + index));
        }

        assertEquals(List.of("message-2", "message-3", "message-4"),
                memory.get("window").stream().map(Message::getText).toList());
    }

    @Test
    void concurrentAddsDoNotLoseMessages() throws Exception {
        FileBasedChatMemoryRepository repository =
                new FileBasedChatMemoryRepository(this.tempDirectory.toString());
        ChatMemory memory = new SynchronizedChatMemory(MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(200)
                .build());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 100; index++) {
                int messageIndex = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    memory.add("concurrent", new UserMessage("message-" + messageIndex));
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
        finally {
            executor.shutdownNow();
        }

        List<String> messages = memory.get("concurrent").stream().map(Message::getText).toList();
        assertEquals(100, messages.size());
        assertEquals(100, new HashSet<>(messages).size());
    }
}
