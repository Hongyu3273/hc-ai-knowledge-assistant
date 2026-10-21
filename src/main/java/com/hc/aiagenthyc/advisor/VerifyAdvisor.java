package com.hc.aiagenthyc.advisor;

import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

import java.util.Set;

/**
 * Rejects requests containing blocked terms before calling the model.
 */
public final class VerifyAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "test prohibited phrase",
            "false advertising",
            "malicious link",
            "fraud"
    );

    private final int order;

    public VerifyAdvisor() {
        this(0);
    }

    public VerifyAdvisor(int order) {
        this.order = order;
    }

    @Override
    public String getName() {
        return VerifyAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userText = request.prompt().getUserMessage().getText();
        if (containsForbiddenWord(userText)) {
            throw refusal();
        }
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String userText = request.prompt().getUserMessage().getText();
        if (containsForbiddenWord(userText)) {
            return Flux.error(refusal());
        }
        return chain.nextStream(request);
    }

    private ChatException refusal() {
        return new ChatException(
                ErrorCode.CONTENT_NOT_ALLOWED,
                "This message contains prohibited content and cannot be answered");
    }

    private boolean containsForbiddenWord(String userText) {
        if (userText == null) {
            return false;
        }
        for (String word : FORBIDDEN_WORDS) {
            if (userText.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
