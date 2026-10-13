package com.hc.aiagenthyc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-key",
        "langchain4j.community.dashscope.chat-model.api-key=test-key",
        "app.gemini.enabled=true", "app.gemini.api-key=test-key", "app.love.model=gemini-2.5-pro"
})
class AiAgentHycApplicationTests {

    @Test
    void contextLoads() {
    }

}
