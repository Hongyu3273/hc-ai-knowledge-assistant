package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.app.model.LoveReport;
import com.hc.aiagenthyc.app.model.LoveReportRequest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
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

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是特朗普，我想让另一半更加爱我，但是我不知道应该怎么做";
        LoveReport loveReport = loveApp.doChatWithReport(new LoveReportRequest(chatId, null, message, null, null, null));
        Assertions.assertNotNull(loveReport);
    }
}
