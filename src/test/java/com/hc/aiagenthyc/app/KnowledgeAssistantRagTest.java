package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import com.hc.aiagenthyc.model.ModelRoute;
import com.hc.aiagenthyc.rag.RagAnswer;
import com.hc.aiagenthyc.rag.RagProperties;
import com.hc.aiagenthyc.support.KnowledgeAssistantTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test of local knowledge-base RAG in {@link KnowledgeAssistant#doChatWithRag}: how retrieved passages reach the
 * prompt and the returned sources, what is saved to memory, content checks, failures and metrics.
 *
 * <p>The vector store and chat model are Mockito mocks, so no embedding model or question banks are needed.</p>
 */
class KnowledgeAssistantRagTest {
    private final ChatModel model = mock(ChatModel.class);
    private final VectorStore store = mock(VectorStore.class);
    private final ChatMemory memory = MessageWindowChatMemory.builder().build();
    // Shared with the assistant, so a test can change retrieval settings after construction.
    private final RagProperties ragProperties = new RagProperties();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final KnowledgeAssistant app = KnowledgeAssistantTestFactory.create(
            Map.of("default", new ModelRoute(ChatClient.create(model), null)), memory,
            KnowledgeAssistantTestFactory.templates(), "default", mock(com.hc.aiagenthyc.attachment.service.AttachmentService.class),
            new com.hc.aiagenthyc.attachment.config.AttachmentProperties(), store, null, ragProperties, meters);

    private void modelAnswers(String text) {
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    private Prompt sentPrompt() {
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        return prompt.getValue();
    }

    @Test
    void ragUsesRetrievedTextButStoresOnlyTheOriginalQuestion() {
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Use a client order ID so a retried order is not placed twice.")));
        modelAnswers("Use an idempotency key.");

        assertThat(app.doChatWithRag("How do I avoid duplicate orders?", "rag-1").content()).isEqualTo("Use an idempotency key.");

        // The passage is added to this call's user message only; memory keeps the question as the user typed it.
        assertThat(sentPrompt().getInstructions()).extracting(message -> message.getText())
                .anySatisfy(text -> assertThat(text).contains("How do I avoid duplicate orders?", "client order ID"));
        assertThat(memory.get("rag-1")).extracting(message -> message.getText())
                .containsExactly("How do I avoid duplicate orders?", "Use an idempotency key.");
    }

    @Test
    void returnsTheSuppliedPassagesAsSources() {
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder().text("Question: When does @Transactional not take effect?\n\nAnswer:\n"
                                + "Self-invocation bypasses the proxy, so the annotation is ignored.")
                        .metadata(Map.of("id", "Q03-305", "bank", "03 Java Backend", "category", "Spring",
                                "filename", "03-java-backend.md",
                                "title", "Q03-305 When does @Transactional not take effect?"))
                        .score(0.82).build()));
        modelAnswers("Self-invocation is the usual cause [Q03-305].");

        RagAnswer answer = app.doChatWithRag("Why is my transaction ignored?", "rag-sources");

        assertThat(answer.sources()).singleElement().satisfies(source -> {
            assertThat(source.id()).isEqualTo("Q03-305");
            assertThat(source.bank()).isEqualTo("03 Java Backend");
            assertThat(source.category()).isEqualTo("Spring");
            assertThat(source.filename()).isEqualTo("03-java-backend.md");
            assertThat(source.title()).isEqualTo("Q03-305 When does @Transactional not take effect?");
            assertThat(source.score()).isEqualTo(0.82);
            // The excerpt skips the "Question: ... Answer:" prefix and starts at the answer text.
            assertThat(source.excerpt()).startsWith("Self-invocation bypasses the proxy");
        });
        assertThat(sentPrompt().getInstructions()).extracting(message -> message.getText())
                // Each passage is labelled with its ID, bank and category so the model can cite it.
                .anySatisfy(text -> assertThat(text).contains("[Q03-305] 03 Java Backend / Spring"));
    }

    @Test
    void passesConfiguredTopKAndThresholdToTheSearch() {
        ragProperties.setTopK(2);
        ragProperties.setSimilarityThreshold(0.45);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("Some advice.")));
        modelAnswers("Answer.");

        app.doChatWithRag("Question?", "rag-config");

        var search = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(search.capture());
        assertThat(search.getValue().getTopK()).isEqualTo(2);
        assertThat(search.getValue().getSimilarityThreshold()).isEqualTo(0.45);
    }

    /**
     * With no passage above the threshold the model is still called, so it can use the conversation history, but the
     * prompt says that the knowledge base had nothing relevant.
     */
    @Test
    void withoutMatchingPassagesTheModelIsToldThereIsNoMaterialButHistoryStillWorks() {
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        modelAnswers("I could not find that in the knowledge base.");

        RagAnswer answer = app.doChatWithRag("What gate is flight ZX-9472?", "rag-empty");

        assertThat(answer.sources()).isEmpty();
        assertThat(sentPrompt().getUserMessage().getText())
                .contains("No passage in the knowledge base matched", "What gate is flight ZX-9472?")
                .doesNotContain("Reference material:");
        assertThat(memory.get("rag-empty")).extracting(message -> message.getText())
                .containsExactly("What gate is flight ZX-9472?", "I could not find that in the knowledge base.");
        assertThat(meters.counter("hc.ai.rag.no.match", "mode", "local").count()).isEqualTo(1.0);
    }

    /**
     * The passage contains the blocked word "fraud". VerifyAdvisor checks only the user's own words, passed through
     * {@code VerifyAdvisor.CHECKED_TEXT}, so retrieved material cannot cause a refusal.
     */
    @Test
    void blockedWordInsideARetrievedPassageDoesNotRejectTheQuestion() {
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("If money was taken through fraud, contact your bank.")));
        modelAnswers("Contact your bank.");

        assertThat(app.doChatWithRag("Someone took my savings, what now?", "rag-passage").content())
                .isEqualTo("Contact your bank.");
    }

    /** The question is checked before retrieval, so a refused question does not spend an embedding call. */
    @Test
    void blockedWordInTheQuestionIsRejectedBeforeRetrieval() {
        assertThatThrownBy(() -> app.doChatWithRag("How do I commit fraud?", "rag-blocked"))
                .isInstanceOfSatisfying(ChatException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTENT_NOT_ALLOWED));
        verifyNoInteractions(store);
        verify(model, never()).call(any(Prompt.class));
        assertThat(memory.get("rag-blocked")).isEmpty();
    }

    @Test
    void retrievalFailureDoesNotSaveAnIncompleteTurn() {
        when(store.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("offline"));

        assertThatThrownBy(() -> app.doChatWithRag("How should I respond?", "rag-2"))
                .isInstanceOfSatisfying(ChatException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MODEL_CALL_FAILED));
        assertThat(memory.get("rag-2")).isEmpty();
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void recordsRetrievalAndModelCallMetrics() {
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("One."), new Document("Two.")));
        modelAnswers("Answer.");

        app.doChatWithRag("Question?", "rag-metrics");

        // The summary records the number of passages per question: one question with two passages.
        assertThat(meters.get("hc.ai.rag.retrieved.documents").tag("mode", "local").summary().totalAmount())
                .isEqualTo(2.0);
        assertThat(meters.get("hc.ai.rag.retrieval").tag("mode", "local").timer().count()).isEqualTo(1);
        assertThat(meters.get("hc.ai.model.calls").tag("operation", "rag").tag("outcome", "success").timer().count())
                .isEqualTo(1);
    }
}
