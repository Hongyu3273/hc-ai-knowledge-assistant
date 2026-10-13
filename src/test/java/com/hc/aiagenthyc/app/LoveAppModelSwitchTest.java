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
        when(dashscope.call(any(Prompt.class))).thenReturn(response("千问回答"));
        when(pro.call(any(Prompt.class))).thenReturn(response("Pro 回答"));
        when(flashLite.call(any(Prompt.class))).thenReturn(response("Lite 回答"));
        app = com.hc.aiagenthyc.support.LoveAppTestFactory.create(clients, MessageWindowChatMemory.builder().build(), templates, "gemini-2.5-pro");
    }

    @Test
    void configuredDefaultAndPerCallOverridesRemainIndependent() {
        assertThat(app.doChat("你好", "a")).isEqualTo("Pro 回答");
        assertThat(app.doChat("你好", "b", "dashscope")).isEqualTo("千问回答");
        assertThat(app.doChat("你好", "c", "gemini-2.5-flash-lite")).isEqualTo("Lite 回答");
        assertThat(app.doChat("再见", "d")).isEqualTo("Pro 回答");
        verify(pro, times(2)).call(any(Prompt.class));
        assertThat(app.availableModels()).containsExactlyInAnyOrderElementsOf(clients.keySet());
    }

    @Test
    void switchingKeepsHistoryAndLoveSystemPromptButOtherChatsAreIsolated() {
        app.doChat("我叫小王", "same", "dashscope");
        app.doChat("我叫什么", "same", "gemini-2.5-pro");
        app.doChat("你好", "different", "gemini-2.5-pro");
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(pro, times(2)).call(prompts.capture());
        Prompt continued = prompts.getAllValues().getFirst();
        assertThat(continued.getSystemMessage().getText()).isEqualTo(templates.renderSystemPrompt());
        assertThat(continued.getInstructions()).extracting(message -> message.getText())
                .contains("我叫小王", "千问回答", "我叫什么");
        assertThat(prompts.getAllValues().getLast().getInstructions()).extracting(message -> message.getText())
                .doesNotContain("我叫小王", "千问回答");
    }

    @Test
    void reportsUseDefaultOrExplicitModel() {
        String json = "{\"title\":\"沟通建议\",\"suggestions\":[\"认真倾听\"]}";
        when(pro.call(any(Prompt.class))).thenReturn(response(json));
        when(flashLite.call(any(Prompt.class))).thenReturn(response(json));
        assertThat(app.doChatWithReport(new LoveReportRequest("r1", null, "如何沟通", null, null, null)).title()).isEqualTo("沟通建议");
        assertThat(app.doChatWithReport(new LoveReportRequest("r2", "gemini-2.5-flash-lite", "如何沟通", null, null, null)).suggestions())
                .containsExactly("认真倾听");
        verify(pro).call(any(Prompt.class));
        verify(flashLite).call(any(Prompt.class));
        verify(dashscope, never()).call(any(Prompt.class));
    }

    @Test
    void invalidSelectionFailsBeforeAnyModelCall() {
        assertThatIllegalArgumentException().isThrownBy(() -> app.doChat("你好", "a", "missing"))
                .withMessageContaining("可用模型");
        assertThatIllegalArgumentException().isThrownBy(() -> com.hc.aiagenthyc.support.LoveAppTestFactory.create(clients,
                MessageWindowChatMemory.builder().build(), templates, "missing"));
        for (ChatModel model : List.of(dashscope, pro, flashLite)) {
            verify(model, never()).call(any(Prompt.class));
        }
    }

    @Test
    void allModelsRetainVerificationAdvisor() {
        for (String model : app.availableModels()) {
            assertThatThrownBy(() -> app.doChat("测试违禁词", model, model))
                    .isInstanceOf(com.hc.aiagenthyc.common.error.ChatException.class).hasMessageContaining("无法回答");
        }
        for (ChatModel model : List.of(dashscope, pro, flashLite)) {
            verify(model, never()).call(any(Prompt.class));
        }
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
