package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.model.ModelRoute;
import com.hc.aiagenthyc.support.LoveAppTestFactory;
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

class LoveAppRagTest {
    private final ChatModel model = mock(ChatModel.class);
    private final VectorStore store = mock(VectorStore.class);
    private final ChatMemory memory = MessageWindowChatMemory.builder().build();
    private final LoveApp app = LoveAppTestFactory.create(
            Map.of("default", new ModelRoute(ChatClient.create(model), null)), memory,
            LoveAppTestFactory.templates(), "default", mock(com.hc.aiagenthyc.attachment.service.AttachmentService.class),
            new com.hc.aiagenthyc.attachment.config.AttachmentProperties(), store);

    @Test
    void ragUsesRetrievedTextButStoresOnlyTheOriginalQuestion() {
        when(store.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Listen without interrupting before offering advice.")));
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Listen first.")))));

        assertThat(app.doChatWithRag("How should I respond?", "rag-1")).isEqualTo("Listen first.");

        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions()).extracting(message -> message.getText())
                .anySatisfy(text -> assertThat(text).contains("How should I respond?", "Listen without interrupting"));
        assertThat(memory.get("rag-1")).extracting(message -> message.getText())
                .containsExactly("How should I respond?", "Listen first.");
    }

    @Test
    void retrievalFailureDoesNotSaveAnIncompleteTurn() {
        when(store.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("offline"));

        assertThatThrownBy(() -> app.doChatWithRag("How should I respond?", "rag-2"))
                .isInstanceOf(com.hc.aiagenthyc.common.error.ChatException.class);
        assertThat(memory.get("rag-2")).isEmpty();
        verify(model, never()).call(any(Prompt.class));
    }
}
