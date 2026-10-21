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
        // First conversation turn
        String message = "I am user 1";
        String answer = loveApp.doChat(message, chatId);
        // Second conversation turn
        message = "Do you think my girlfriend Java loves me?";
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // Third conversation turn
        message = "What is my girlfriend's name again?";
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "Hello, I am Trump. I want my partner to love me more, but I do not know what to do.";
        LoveReport loveReport = loveApp.doChatWithReport(new LoveReportRequest(chatId, null, message, null, null, null));
        Assertions.assertNotNull(loveReport);
    }
}
