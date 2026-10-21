package com.hc.aiagenthyc.config;

import com.hc.aiagenthyc.model.ModelRoute;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GeminiConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GeminiConfiguration.class, LoveModelConfiguration.class)
            .withBean("dashScopeChatModel", ChatModel.class, () -> mock(ChatModel.class));

    @Test
    void disabledByDefaultDoesNotRequireKey() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(Client.class);
            assertThat(context).hasSingleBean(ChatModel.class);
        });
    }

    @Test
    void enabledCreatesOneSharedClientWithoutChangingDefaultModel() {
        runner.withPropertyValues("app.gemini.enabled=true", "app.gemini.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(Client.class);
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBeansOfType(ChatClient.class))
                            .containsOnlyKeys("geminiChatClient");
                    @SuppressWarnings("unchecked")
                    var routes = (java.util.Map<String, com.hc.aiagenthyc.model.ModelRoute>)
                            context.getBean("loveAppModelClients");
                    assertThat(routes.get("gemini-2.5-pro").client())
                            .isSameAs(routes.get("gemini-2.5-flash-lite").client());
                });
    }

    @Test
    void enabledWithoutKeyFailsWithActionableMessage() {
        runner.withPropertyValues("app.gemini.enabled=true", "app.gemini.api-key=")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("GEMINI_API_KEY or app.gemini.api-key must be configured when Gemini is enabled"));
    }
}
