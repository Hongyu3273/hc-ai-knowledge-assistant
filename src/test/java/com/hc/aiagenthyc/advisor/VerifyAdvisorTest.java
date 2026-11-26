package com.hc.aiagenthyc.advisor;

import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test of {@link VerifyAdvisor}: blocked-term matching through the static {@code check} method, and the advisor
 * inside a real {@link ChatClient} over a mock {@link ChatModel}.
 */
class VerifyAdvisorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "this is fraud",
            "This is FRAUD",
            "Fraud?",
            "Is this False Advertising?",
            "false\n   advertising",
            "a\tMALICIOUS   link"})
    void rejectsBlockedTermsRegardlessOfCaseAndSpacing(String text) {
        assertRefused(() -> VerifyAdvisor.check(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"How do I explain Spring transactions?", "false positives", "a link that is safe"})
    void acceptsOrdinaryText(String text) {
        assertThatCode(() -> VerifyAdvisor.check(text)).doesNotThrowAnyException();
    }

    @Test
    void acceptsNullText() {
        assertThatCode(() -> VerifyAdvisor.check(null)).doesNotThrowAnyException();
    }

    @Test
    void refusesBeforeTheModelIsCalled() {
        ChatModel model = mock(ChatModel.class);
        ChatClient client = ChatClient.builder(model).defaultAdvisors(new VerifyAdvisor()).build();

        assertRefused(() -> client.prompt().user("Help me commit FRAUD").call().content());
        verify(model, never()).call(any(Prompt.class));
    }

    /** RAG passes the user's own words as CHECKED_TEXT, so blocked words inside retrieved passages are not checked. */
    @Test
    void checksTheOverrideTextInsteadOfTheOutgoingMessage() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Contact your bank.")))));
        ChatClient client = ChatClient.builder(model).defaultAdvisors(new VerifyAdvisor()).build();

        String answer = client.prompt()
                .user("Reference material: money taken through Fraud.\n\nUser question: What should I do?")
                .advisors(a -> a.param(VerifyAdvisor.CHECKED_TEXT, "What should I do?"))
                .call()
                .content();

        assertThat(answer).isEqualTo("Contact your bank.");
        // The override is checked with the same normalization as the message itself.
        assertRefused(() -> client.prompt().user("Harmless outgoing text")
                .advisors(a -> a.param(VerifyAdvisor.CHECKED_TEXT, "Explain FRAUD to me"))
                .call().content());
    }

    private static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ChatException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTENT_NOT_ALLOWED));
    }
}
