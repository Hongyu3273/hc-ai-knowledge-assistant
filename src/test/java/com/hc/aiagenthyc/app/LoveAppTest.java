package com.hc.aiagenthyc.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class LoveAppTest {

    @Resource
    private LoveApp loveApp;
    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        //第一轮
        String message = "我是用户1";
        String answer = loveApp.doChat(message, chatId);
        //第二轮
        message = "你觉得我gf Java爱我吗";
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        //第三轮
        message = "我gf叫什么来着？";
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }
}