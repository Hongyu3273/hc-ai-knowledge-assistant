package com.hc.aiagenthyc.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 保证同一 JVM 内针对同一会话的 ChatMemory 操作按顺序执行。
 */
public final class SynchronizedChatMemory implements ChatMemory {

    private static final int LOCK_STRIPES = 64;

    private final ChatMemory delegate;

    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    public SynchronizedChatMemory(ChatMemory delegate) {
        Assert.notNull(delegate, "delegate cannot be null");
        this.delegate = delegate;
        for (int index = 0; index < this.locks.length; index++) {
            this.locks[index] = new ReentrantLock();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        withLock(conversationId, () -> {
            this.delegate.add(conversationId, messages);
            return null;
        });
    }

    @Override
    public List<Message> get(String conversationId) {
        return withLock(conversationId, () -> this.delegate.get(conversationId));
    }

    @Override
    public void clear(String conversationId) {
        withLock(conversationId, () -> {
            this.delegate.clear(conversationId);
            return null;
        });
    }

    private <T> T withLock(String conversationId, Operation<T> operation) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        ReentrantLock lock = this.locks[Math.floorMod(conversationId.hashCode(), this.locks.length)];
        lock.lock();
        try {
            return operation.execute();
        }
        finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    private interface Operation<T> {

        T execute();
    }
}
