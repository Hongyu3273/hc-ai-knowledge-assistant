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
    /**
     * Runs the operation while holding the lock stripe for this conversation, so concurrent requests with the same
     * {@code chatId} cannot interleave their memory reads and writes. The lock is held for the whole operation,
     * including the model call, and is reentrant within the same thread.
     *
     * @param chatId conversation ID; must not be {@code null}
     * @param operation work to run under the lock
     * @return the operation's result
     */
    public <T> T execute(String chatId, Supplier<T> operation) {
        ReentrantLock lock = locks[Math.floorMod(chatId.hashCode(), locks.length)];
        lock.lock();
        try { return operation.get(); }
        finally { lock.unlock(); }
    }
}
