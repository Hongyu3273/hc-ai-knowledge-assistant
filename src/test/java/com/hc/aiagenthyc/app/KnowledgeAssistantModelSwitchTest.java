package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.app.model.StudyReportRequest;
import com.hc.aiagenthyc.app.prompt.AssistantPromptTemplates;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import com.hc.aiagenthyc.model.ModelRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test of model routing in {@link KnowledgeAssistant}: the configured default route, per-call overrides, shared
 * history across a model switch, and validation of unknown model IDs. Each route wraps its own Mockito
 * {@link ChatModel}, so the test can tell which provider received a call.
 */
class KnowledgeAssistantModelSwitchTest {
    private final ChatModel dashscope = mock(ChatModel.class);
    private final ChatModel pro = mock(ChatModel.class);
    private final ChatModel flashLite = mock(ChatModel.class);
    private AssistantPromptTemplates templates;
    private Map<String, ModelRoute> clients;
    private KnowledgeAssistant app;

    @BeforeEach
    void setUp() {
        templates = new AssistantPromptTemplates(new ClassPathResource("prompts/assistant/system.st"),
                new ClassPathResource("prompts/assistant/report-instruction.st"));
        // A null model keeps the provider's configured default, as the real DashScope route does.
        clients = Map.of("dashscope", new ModelRoute(ChatClient.create(dashscope), null),
                "gemini-2.5-pro", new ModelRoute(ChatClient.create(pro), "gemini-2.5-pro"),
                "gemini-2.5-flash-lite", new ModelRoute(ChatClient.create(flashLite), "gemini-2.5-flash-lite"));
        when(dashscope.call(any(Prompt.class))).thenReturn(response("Qwen response"));
        when(pro.call(any(Prompt.class))).thenReturn(response("Pro response"));
        when(flashLite.call(any(Prompt.class))).thenReturn(response("Lite response"));
        app = com.hc.aiagenthyc.support.KnowledgeAssistantTestFactory.create(clients, MessageWindowChatMemory.builder().build(), templates, "gemini-2.5-pro");
    }

    @Test
    void configuredDefaultAndPerCallOverridesRemainIndependent() {
        assertThat(app.doChat("Hello", "a")).isEqualTo("Pro response");
        assertThat(app.doChat("Hello", "b", "dashscope")).isEqualTo("Qwen response");
        assertThat(app.doChat("Hello", "c", "gemini-2.5-flash-lite")).isEqualTo("Lite response");
        assertThat(app.doChat("Goodbye", "d")).isEqualTo("Pro response");
        // Only the two calls without a model reach the default route; the overrides did not change the default.
        verify(pro, times(2)).call(any(Prompt.class));
        assertThat(app.availableModels()).containsExactlyInAnyOrderElementsOf(clients.keySet());
    }

    @Test
    void switchingKeepsHistoryAndAssistantSystemPromptButOtherChatsAreIsolated() {
        app.doChat("My name is Wang", "same", "dashscope");
        app.doChat("What is my name?", "same", "gemini-2.5-pro");
        app.doChat("Hello", "different", "gemini-2.5-pro");
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(pro, times(2)).call(prompts.capture());
        // The first Pro call continues chat "same", whose first turn was answered by DashScope.
        Prompt continued = prompts.getAllValues().getFirst();
        assertThat(continued.getSystemMessage().getText()).isEqualTo(templates.renderSystemPrompt());
        assertThat(continued.getInstructions()).extracting(message -> message.getText())
                .contains("My name is Wang", "Qwen response", "What is my name?");
        assertThat(prompts.getAllValues().getLast().getInstructions()).extracting(message -> message.getText())
                .doesNotContain("My name is Wang", "Qwen response");
    }

    @Test
    void reportsUseDefaultOrExplicitModel() {
        // A minimal StudyReport: parsing requires a non-blank title and at least one suggestion.
        String json = "{\"title\":\"Communication advice\",\"suggestions\":[\"Listen carefully\"]}";
        when(pro.call(any(Prompt.class))).thenReturn(response(json));
        when(flashLite.call(any(Prompt.class))).thenReturn(response(json));
        assertThat(app.doChatWithReport(new StudyReportRequest("r1", null, "How can we communicate?", null, null, null)).title()).isEqualTo("Communication advice");
        assertThat(app.doChatWithReport(new StudyReportRequest("r2", "gemini-2.5-flash-lite", "How can we communicate?", null, null, null)).suggestions())
                .containsExactly("Listen carefully");
        verify(pro).call(any(Prompt.class));
        verify(flashLite).call(any(Prompt.class));
        verify(dashscope, never()).call(any(Prompt.class));
    }

    @Test
    void invalidSelectionFailsBeforeAnyModelCall() {
        // The message lists the valid IDs so the caller can correct the request.
        assertThatThrownBy(() -> app.doChat("Hello", "a", "missing"))
                .isInstanceOfSatisfying(ChatException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MODEL_NOT_AVAILABLE))
                .hasMessageContaining("available models");
        // An unknown default model fails when the catalog is built, not on the first request.
        assertThatIllegalArgumentException().isThrownBy(() -> com.hc.aiagenthyc.support.KnowledgeAssistantTestFactory.create(clients,
                MessageWindowChatMemory.builder().build(), templates, "missing"));
        for (ChatModel model : List.of(dashscope, pro, flashLite)) {
            verify(model, never()).call(any(Prompt.class));
        }
    }

    /** KnowledgeAssistant adds VerifyAdvisor to every route's client, so a blocked phrase is refused on any model. */
    @Test
    void allModelsRetainVerificationAdvisor() {
        for (String model : app.availableModels()) {
            // The model ID doubles as the chat ID, giving each route its own conversation.
            assertThatThrownBy(() -> app.doChat("test prohibited phrase", model, model))
                    .isInstanceOfSatisfying(ChatException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTENT_NOT_ALLOWED));
        }
        for (ChatModel model : List.of(dashscope, pro, flashLite)) {
            verify(model, never()).call(any(Prompt.class));
        }
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
