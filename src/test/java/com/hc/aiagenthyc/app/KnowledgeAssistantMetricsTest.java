package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.model.ModelRoute;
import com.hc.aiagenthyc.rag.RagProperties;
import com.hc.aiagenthyc.support.KnowledgeAssistantTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test of the Micrometer meters that {@link KnowledgeAssistant} records through {@code MetricsAdvisor}.
 * The chat model is a Mockito mock and the meters go to a {@link SimpleMeterRegistry} the test inspects.
 */
class KnowledgeAssistantMetricsTest {
    private final ChatModel model = mock(ChatModel.class);
    // Passed to the factory so the test can read back what the advisor registered.
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final KnowledgeAssistant app = KnowledgeAssistantTestFactory.create(
            Map.of("default", new ModelRoute(ChatClient.create(model), "qwen-plus")),
            MessageWindowChatMemory.builder().build(), KnowledgeAssistantTestFactory.templates(), "default",
            mock(AttachmentService.class), new AttachmentProperties(), mock(VectorStore.class), null,
            new RagProperties(), meters);

    @Test
    void recordsLatencyAndTokenUsageForAChatCall() {
        // The advisor tags meters with the model the response reports and counts prompt and completion tokens.
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("qwen-plus")
                .usage(new DefaultUsage(120, 30))
                .build();
        when(model.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hello."))), metadata));

        app.doChat("Hi", "metrics-1");

        assertThat(meters.get("hc.ai.model.calls").tags("operation", "chat", "model", "qwen-plus", "outcome", "success")
                .timer().count()).isEqualTo(1);
        assertThat(meters.get("hc.ai.model.tokens").tags("operation", "chat", "type", "prompt").counter().count())
                .isEqualTo(120.0);
        assertThat(meters.get("hc.ai.model.tokens").tags("operation", "chat", "type", "completion").counter().count())
                .isEqualTo(30.0);
    }

    /**
     * MetricsAdvisor runs before VerifyAdvisor, so a refused message is still timed, with the error code as the
     * outcome, even though the model is never called.
     */
    @Test
    void blockedRequestIsRecordedWithItsErrorCode() {
        // "fraud" is one of VerifyAdvisor's blocked terms.
        assertThatThrownBy(() -> app.doChat("this is fraud", "metrics-2")).isInstanceOf(ChatException.class);

        assertThat(meters.get("hc.ai.model.calls").tags("operation", "chat", "outcome", "CONTENT_NOT_ALLOWED")
                .timer().count()).isEqualTo(1);
        verify(model, never()).call(any(Prompt.class));
    }
}
