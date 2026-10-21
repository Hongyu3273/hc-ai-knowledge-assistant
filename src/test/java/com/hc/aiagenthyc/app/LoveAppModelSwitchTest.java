package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.app.model.LoveReportRequest;
import com.hc.aiagenthyc.app.prompt.LovePromptTemplates;
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

class LoveAppModelSwitchTest {
    private final ChatModel dashscope = mock(ChatModel.class);
    private final ChatModel pro = mock(ChatModel.class);
    private final ChatModel flashLite = mock(ChatModel.class);
    private LovePromptTemplates templates;
    private Map<String, ModelRoute> clients;
    private LoveApp app;

    @BeforeEach
    void setUp() {
        templates = new LovePromptTemplates(new ClassPathResource("prompts/love/system.st"),
                new ClassPathResource("prompts/love/report-instruction.st"));
        clients = Map.of("dashscope", new ModelRoute(ChatClient.create(dashscope), null),
                "gemini-2.5-pro", new ModelRoute(ChatClient.create(pro), "gemini-2.5-pro"),
                "gemini-2.5-flash-lite", new ModelRoute(ChatClient.create(flashLite), "gemini-2.5-flash-lite"));
        when(dashscope.call(any(Prompt.class))).thenReturn(response("Qwen response"));
        when(pro.call(any(Prompt.class))).thenReturn(response("Pro response"));
        when(flashLite.call(any(Prompt.class))).thenReturn(response("Lite response"));
        app = com.hc.aiagenthyc.support.LoveAppTestFactory.create(clients, MessageWindowChatMemory.builder().build(), templates, "gemini-2.5-pro");
    }

    @Test
    void configuredDefaultAndPerCallOverridesRemainIndependent() {
        assertThat(app.doChat("Hello", "a")).isEqualTo("Pro response");
        assertThat(app.doChat("Hello", "b", "dashscope")).isEqualTo("Qwen response");
        assertThat(app.doChat("Hello", "c", "gemini-2.5-flash-lite")).isEqualTo("Lite response");
        assertThat(app.doChat("Goodbye", "d")).isEqualTo("Pro response");
        verify(pro, times(2)).call(any(Prompt.class));
        assertThat(app.availableModels()).containsExactlyInAnyOrderElementsOf(clients.keySet());
    }

    @Test
    void switchingKeepsHistoryAndLoveSystemPromptButOtherChatsAreIsolated() {
        app.doChat("My name is Wang", "same", "dashscope");
        app.doChat("What is my name?", "same", "gemini-2.5-pro");
        app.doChat("Hello", "different", "gemini-2.5-pro");
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(pro, times(2)).call(prompts.capture());
        Prompt continued = prompts.getAllValues().getFirst();
        assertThat(continued.getSystemMessage().getText()).isEqualTo(templates.renderSystemPrompt());
        assertThat(continued.getInstructions()).extracting(message -> message.getText())
                .contains("My name is Wang", "Qwen response", "What is my name?");
        assertThat(prompts.getAllValues().getLast().getInstructions()).extracting(message -> message.getText())
                .doesNotContain("My name is Wang", "Qwen response");
    }

    @Test
    void reportsUseDefaultOrExplicitModel() {
        String json = "{\"title\":\"Communication advice\",\"suggestions\":[\"Listen carefully\"]}";
        when(pro.call(any(Prompt.class))).thenReturn(response(json));
        when(flashLite.call(any(Prompt.class))).thenReturn(response(json));
        assertThat(app.doChatWithReport(new LoveReportRequest("r1", null, "How can we communicate?", null, null, null)).title()).isEqualTo("Communication advice");
        assertThat(app.doChatWithReport(new LoveReportRequest("r2", "gemini-2.5-flash-lite", "How can we communicate?", null, null, null)).suggestions())
                .containsExactly("Listen carefully");
        verify(pro).call(any(Prompt.class));
        verify(flashLite).call(any(Prompt.class));
        verify(dashscope, never()).call(any(Prompt.class));
    }

    @Test
    void invalidSelectionFailsBeforeAnyModelCall() {
        assertThatIllegalArgumentException().isThrownBy(() -> app.doChat("Hello", "a", "missing"))
                .withMessageContaining("available models");
        assertThatIllegalArgumentException().isThrownBy(() -> com.hc.aiagenthyc.support.LoveAppTestFactory.create(clients,
                MessageWindowChatMemory.builder().build(), templates, "missing"));
        for (ChatModel model : List.of(dashscope, pro, flashLite)) {
            verify(model, never()).call(any(Prompt.class));
        }
    }

    @Test
    void allModelsRetainVerificationAdvisor() {
        for (String model : app.availableModels()) {
            assertThatThrownBy(() -> app.doChat("test prohibited phrase", model, model))
                    .isInstanceOf(com.hc.aiagenthyc.common.error.ChatException.class).hasMessageContaining("cannot be answered");
        }
        for (ChatModel model : List.of(dashscope, pro, flashLite)) {
            verify(model, never()).call(any(Prompt.class));
        }
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
