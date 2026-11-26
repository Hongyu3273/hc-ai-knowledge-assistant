package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.app.model.StudyReport;
import com.hc.aiagenthyc.app.model.StudyReportRequest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live smoke test of {@link KnowledgeAssistant} chat and study reports against the configured default model.
 *
 * <p>Runs only with {@code RUN_LIVE_AI_TESTS=true}. It loads the full application with the default "local" profile,
 * so it needs a real DashScope key (application-local.yml or {@code AI_DASHSCOPE_API_KEY}), calls the provider and
 * consumes quota. Conversation memory is written to a per-run directory under {@code target/live-tests}, not to the
 * application's own chat-memory directory.</p>
 */
@Tag("live-ai")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KnowledgeAssistantTest {

    private static final Path RUN_DIRECTORY = Path.of("target", "live-tests", UUID.randomUUID().toString())
            .toAbsolutePath();

    @Resource
    private KnowledgeAssistant assistant;

    // Keep test conversations out of the development chat history.
    @DynamicPropertySource
    static void isolateConversationStorage(DynamicPropertyRegistry registry) {
        registry.add("app.chat-memory.directory", () -> RUN_DIRECTORY.resolve("memory").toString());
    }

    /** The third turn can only be answered from memory, so it checks that the first two turns were stored. */
    @Test
    void remembersDetailsFromEarlierTurns() {
        String chatId = UUID.randomUUID().toString();

        String first = assistant.doChat("Hi, I am getting ready for software engineering interviews.", chatId);
        assertThat(first).isNotBlank();

        String second = assistant.doChat(
                "My name is Chen and I am preparing for Java backend interviews. What should I revise first?", chatId);
        assertThat(second).isNotBlank();

        String third = assistant.doChat("What is my name, and which interviews am I preparing for?", chatId);
        assertThat(third.toLowerCase(Locale.ROOT))
                .as("the model should recall the name and the interview track from memory")
                .contains("chen")
                .contains("java");
    }

    @Test
    void producesAStructuredStudyReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "I am Chen. I can explain Spring IoC but I struggle with transaction propagation and "
                + "@Transactional pitfalls.";

        StudyReport report = assistant.doChatWithReport(
                new StudyReportRequest(chatId, null, message, null, null, null));

        // doChatWithReport already rejects a missing title or empty suggestions; these checks state the contract.
        assertThat(report.title()).isNotBlank();
        assertThat(report.suggestions()).isNotEmpty().allSatisfy(s -> assertThat(s).isNotBlank());
        assertThat(String.join(" ", report.suggestions()).toLowerCase(Locale.ROOT))
                .as("the suggestions should address the weak area the user named")
                .containsAnyOf("transaction", "propagation", "@transactional");
    }
}
