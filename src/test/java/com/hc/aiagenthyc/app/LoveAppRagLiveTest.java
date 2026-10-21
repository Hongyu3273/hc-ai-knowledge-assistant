package com.hc.aiagenthyc.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live conversation tests using the full application, actual embedding and chat models, and the knowledge base without mocks.
 * Run only when RUN_LIVE_AI_TESTS=true; configure application-local.yml or the corresponding environment variables first.
 * Runs call real services and consume quota. Answers and retrieval sources are saved under target/rag-live-tests for review.
 * Assertions check behavior and semantic cues without exact wording; human review is still needed to assess accuracy and tone.
 */
@Tag("live-ai")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class LoveAppRagLiveTest {

    private static final Path RUN_DIRECTORY = Path.of("target", "rag-live-tests", UUID.randomUUID().toString())
            .toAbsolutePath();

    @Autowired
    private LoveApp app;

    @Autowired
    @Qualifier("loveAppVectorStore")
    private VectorStore store;

    @Autowired
    @Qualifier("loveAppChatMemory")
    private ChatMemory memory;

    private final List<String> chatIds = new ArrayList<>();
    private final StringBuilder transcript = new StringBuilder();

    // Isolate test history from development conversations while retaining the application's actual knowledge base and model configuration.
    @DynamicPropertySource
    static void isolateConversationStorage(DynamicPropertyRegistry registry) {
        registry.add("app.chat-memory.directory", () -> RUN_DIRECTORY.resolve("memory").toString());
        registry.add("app.chat-memory.max-messages", () -> 10);
    }

    /**
     * Retrieve conflict advice and check that a single-turn answer suggests both a pause and a plan to resume discussion.
     * Check actual retrieved evidence and actionable model advice instead of supplying a predefined model response.
     */
    @Test
    @DisplayName("Live RAG: conflict advice includes a pause and a plan to resume")
    void givesGroundedConflictAdvice() {
        String question = "My partner and I blame each other whenever we disagree. "
                + "According to the relationship FAQ, how can we stop an argument from escalating? "
                + "Please answer in English with a concrete pause and a plan to resume the discussion.";
        String evidence = recordRetrieval(question);
        assertThat(evidence.toLowerCase(Locale.ROOT))
                .as("Retrieved evidence should discuss pausing or calming down, rather than merely being nonempty")
                .containsPattern("pause|calm|cool|de-escalat");

        String answer = ask(newChat(), question);
        assertThat(answer.toLowerCase(Locale.ROOT))
                .as("The answer should suggest a pause and resuming discussion; review the transcript for detailed quality")
                .containsPattern("pause|break|calm|cool|time.?out")
                .containsPattern("resume|return|revisit|reconvene|talk again|discuss again|come back");
    }

    /**
     * Provide a name and duration absent from the knowledge base, ask a follow-up, then check recall across three turns.
     * Reuse one chatId and omit the name and duration from the final question to test the effect of actual conversation history.
     */
    @Test
    @DisplayName("Live RAG: three turns retain the partner's name and agreed pause duration")
    void remembersPersonalDetailsAcrossThreeTurns() {
        String chatId = newChat();
        ask(chatId, "My partner's name is Morgan. We agreed on a 23-minute pause when arguments escalate. "
                + "We often blame each other. Using the relationship FAQ, suggest a calmer approach. Answer in English.");
        String followUp = ask(chatId, "After that pause, how should we restart the discussion? "
                + "Give me one concrete sentence I could say. Answer in English.");
        assertThat(followUp.toLowerCase(Locale.ROOT))
                .as("The follow-up should provide advice about restarting the discussion")
                .containsPattern("feel|felt|listen|understand|discuss|talk|share|need");

        String recall = ask(chatId, "From my earlier messages in this conversation, "
                + "what is my partner's name and how many minutes did we agree to pause? "
                + "Use our conversation history for those personal details. Answer in English.");
        assertThat(recall.toLowerCase(Locale.ROOT))
                .as("Recall details unique to this conversation; failure may reveal a conflict between the RAG prompt and history use")
                .contains("morgan")
                .containsPattern("23|twenty[ -]three");
        assertThat(memory.get(chatId)).as("Three successful turns should save six messages").hasSize(6);
    }

    /**
     * Provide a unique name in conversation A and ensure conversation B cannot use A's details as its own context.
     * Return to A to verify its details remain available, checking both recall and conversation isolation.
     */
    @Test
    @DisplayName("Live RAG: separate chat IDs do not share partner names")
    void keepsPersonalDetailsSeparateBetweenConversations() {
        String firstChat = newChat();
        String secondChat = newChat();
        ask(firstChat, "My partner's name is AveryQuinn. We argue about looking at phones while talking. "
                + "Give one suggestion from the relationship FAQ. Answer in English.");
        String otherAnswer = ask(secondChat, "What is my partner's name? "
                + "If I have not told you in this conversation, say you do not know. Answer in English.");
        assertThat(otherAnswer.toLowerCase(Locale.ROOT))
                .as("A new conversation must not reveal a name unique to another conversation")
                .doesNotContain("averyquinn")
                .containsPattern("don't know|do not know|haven't|have not|not (?:been )?(?:told|provided|shared|mentioned)|cannot|can't|unknown|no information");

        String firstAnswer = ask(firstChat, "What is my partner's name, according to my earlier message "
                + "in this conversation? Answer in English.");
        assertThat(firstAnswer.toLowerCase(Locale.ROOT)).contains("averyquinn");
    }

    /**
     * The knowledge base contains relationship advice, not boarding gates for fictional flights.
     * Even if retrieval returns documents, the model should acknowledge missing information rather than invent a gate.
     */
    @Test
    @DisplayName("Live RAG: questions outside the knowledge base acknowledge missing information")
    void acknowledgesMissingKnowledgeInsteadOfInventingAnAnswer() {
        String question = "According only to the supplied relationship FAQ, what is the exact boarding gate "
                + "for fictional flight ZX-9472 at Imaginary Airport tomorrow? "
                + "If the reference material has no relevant information, explicitly say so. Answer in English.";
        recordRetrieval(question);
        String answer = ask(newChat(), question);
        assertThat(answer.toLowerCase(Locale.ROOT))
                .as("Explicitly acknowledge missing reference information rather than merely return nonempty text")
                .containsPattern("no relevant|not (?:contain|include|provide|cover)|doesn't (?:contain|include|provide|cover)|could not find|cannot|can't|unable|unavailable|don't have|do not have|no information|not available|not found|not related|unrelated");
        assertThat(answer.toLowerCase(Locale.ROOT))
                .as("Do not invent a specific boarding gate; this heuristic still requires human review of the full answer")
                .doesNotContainPattern("(?:gate (?:is|will be|number)|depart(?:s|ing)? from gate)\\s*[:#]?\\s*[a-z]?\\d+");
    }

    // Create unique conversation IDs so test execution order does not affect results.
    private String newChat() {
        String chatId = "rag-live-" + UUID.randomUUID();
        chatIds.add(chatId);
        return chatId;
    }

    // Call the actual business entry point and retain full exchanges for diagnosis even when assertions fail.
    private String ask(String chatId, String question) {
        transcript.append("\n### User (chatId: ").append(chatId).append(")\n\n").append(question).append('\n');
        String answer = app.doChatWithRag(question, chatId);
        transcript.append("\n### Assistant\n\n").append(answer).append('\n');
        assertThat(answer).as("The real model should return a nonblank answer").isNotBlank();
        return answer;
    }

    // Run an additional real retrieval and record sources to help distinguish poor retrieval from poor use of evidence.
    // This diagnostic query is separate from business retrieval and does not capture the business method's internal request.
    private String recordRetrieval(String question) {
        var documents = store.similaritySearch(SearchRequest.builder().query(question).topK(4).build());
        transcript.append("\n## Diagnostic retrieval\n\nQuery: ").append(question).append('\n');
        assertThat(documents).as("The indexed knowledge base should return candidate documents").isNotEmpty();
        StringBuilder evidence = new StringBuilder();
        for (var document : documents) {
            transcript.append("\nSource: ").append(document.getMetadata().get("filename"))
                    .append("\n\n").append(document.getText()).append('\n');
            evidence.append(document.getText()).append('\n');
        }
        return evidence.toString();
    }

    // Save actual exchanges after each test, including assertion failures, and clear isolated test conversations.
    @AfterEach
    void saveTranscriptAndCleanMemory(TestInfo testInfo) throws IOException {
        String method = testInfo.getTestMethod().orElseThrow().getName();
        Files.createDirectories(RUN_DIRECTORY);
        Path output = RUN_DIRECTORY.resolve(method + ".md");
        Files.writeString(output, "# " + testInfo.getDisplayName() + "\n" + transcript, StandardCharsets.UTF_8);
        System.out.println("RAG live transcript: " + output);
        try {
            System.out.println(transcript);
        } finally {
            for (String chatId : chatIds) {
                memory.clear(chatId);
            }
        }
    }
}
