package com.hc.aiagenthyc.memory;

import org.springframework.stereotype.Component;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes the read-call-save sequence per conversation in one application instance; multiple instances need distributed coordination. */
@Component
public class ConversationExecutor {
    // A fixed lock pool bounds memory use. Different conversation IDs can share a stripe.
    private final ReentrantLock[] locks = new ReentrantLock[256];
    public ConversationExecutor() {
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock();
    }
    public <T> T execute(String chatId, Supplier<T> operation) {
        ReentrantLock lock = locks[Math.floorMod(chatId.hashCode(), locks.length)];
        lock.lock();
        try { return operation.get(); }
        finally { lock.unlock(); }
    }
}
