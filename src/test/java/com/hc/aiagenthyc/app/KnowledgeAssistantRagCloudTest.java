package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import com.hc.aiagenthyc.model.ModelRoute;
import com.hc.aiagenthyc.rag.RagAnswer;
import com.hc.aiagenthyc.rag.RagProperties;
import com.hc.aiagenthyc.support.KnowledgeAssistantTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test of cloud RAG in {@link KnowledgeAssistant#doChatWithRagCloud}. A real
 * {@link RetrievalAugmentationAdvisor} runs over a mock {@link DocumentRetriever} that stands in for the DashScope
 * knowledge-base index, so no DashScope key or cloud index is needed. The chat model is a Mockito mock.
 */
class KnowledgeAssistantRagCloudTest {
    private final ChatModel model = mock(ChatModel.class);
    private final DocumentRetriever retriever = mock(DocumentRetriever.class);
    // The local index must stay untouched: cloud RAG retrieves only through the advisor.
    private final VectorStore localStore = mock(VectorStore.class);
    private final MessageWindowChatMemory memory = MessageWindowChatMemory.builder().build();
    private final KnowledgeAssistant app = KnowledgeAssistantTestFactory.create(
            Map.of("default", new ModelRoute(ChatClient.create(model), null)), memory,
            KnowledgeAssistantTestFactory.templates(), "default", mock(AttachmentService.class),
            new AttachmentProperties(), localStore,
            RetrievalAugmentationAdvisor.builder().documentRetriever(retriever).build());

    @Test
    void cloudRetrievalAugmentsPromptWithoutSavingDocuments() {
        when(retriever.retrieve(any())).thenReturn(List.of(new Document("Cloud guidance: listen first.")));
        when(model.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Listen first.")))));

        assertThat(app.doChatWithRagCloud("How should I respond?", "cloud-1").content()).isEqualTo("Listen first.");

        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        // The advisor's query augmenter adds the retrieved text to the outgoing user message only.
        assertThat(prompt.getValue().getUserMessage().getText())
                .contains("How should I respond?", "Cloud guidance: listen first.");
        assertThat(memory.get("cloud-1")).extracting(message -> message.getText())
                .containsExactly("How should I respond?", "Listen first.");
        verifyNoInteractions(localStore);
    }

    @Test
    void cloudAnswerReturnsRetrievedDocumentsAsSources() {
        when(retriever.retrieve(any())).thenReturn(List.of(Document.builder()
                .text("Cloud guidance: listen first.")
                .metadata(Map.of("filename", "cloud-faq.md"))
                .build()));
        when(model.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Listen first.")))));

        // Sources are read from the documents the advisor put in the response context.
        RagAnswer answer = app.doChatWithRagCloud("How should I respond?", "cloud-sources");

        assertThat(answer.sources()).singleElement()
                .satisfies(source -> assertThat(source.filename()).isEqualTo("cloud-faq.md"));
    }

    @Test
    void cloudRetrievalFailureDoesNotSaveAnIncompleteTurn() {
        when(retriever.retrieve(any())).thenThrow(new IllegalStateException("offline"));

        assertThatThrownBy(() -> app.doChatWithRagCloud("How should I respond?", "cloud-2"))
                .isInstanceOfSatisfying(ChatException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MODEL_CALL_FAILED));
        assertThat(memory.get("cloud-2")).isEmpty();
        verify(model, never()).call(any(Prompt.class));
        verifyNoInteractions(localStore);
    }

    /** A null cloud advisor stands for app.assistant.rag.cloud.enabled=false. */
    @Test
    void disabledCloudRetrievalIsReportedAsUnavailable() {
        KnowledgeAssistant withoutCloud = KnowledgeAssistantTestFactory.create(
                Map.of("default", new ModelRoute(ChatClient.create(model), null)), memory,
                KnowledgeAssistantTestFactory.templates(), "default", mock(AttachmentService.class),
                new AttachmentProperties(), localStore, null, new RagProperties(), new SimpleMeterRegistry());

        assertThatThrownBy(() -> withoutCloud.doChatWithRagCloud("How should I respond?", "cloud-off"))
                .isInstanceOfSatisfying(ChatException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MODEL_NOT_AVAILABLE));
        // ChatClient.create reads the model's default options, so only check that no call was made.
        verify(model, never()).call(any(Prompt.class));
        verifyNoInteractions(localStore);
    }
}
