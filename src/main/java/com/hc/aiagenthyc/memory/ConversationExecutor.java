package com.hc.aiagenthyc.memory;

import org.springframework.stereotype.Component;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 单实例会话事务边界：读历史、调用模型、保存结果串行执行。多实例需分布式实现。 */
@Component
public class ConversationExecutor {
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
