package com.hc.aiagenthyc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test that the full Spring application context starts with every provider wired, including both Gemini routes
 * and Gemini Pro as the default assistant model.
 *
 * <p>Needs no API keys or network: the placeholder keys only let the provider beans be constructed, no model is
 * called, and the knowledge-base vector store is lazy, so nothing is embedded at startup.</p>
 */
// The "test" profile replaces the default "local" profile, so application-local.yml (real keys) is not loaded.
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-key",
        "langchain4j.community.dashscope.chat-model.api-key=test-key",
        "app.gemini.enabled=true", "app.gemini.api-key=test-key", "app.assistant.model=gemini-2.5-pro"
})
class AiAgentHycApplicationTests {

    @Test
    void contextLoads() {
    }

}
