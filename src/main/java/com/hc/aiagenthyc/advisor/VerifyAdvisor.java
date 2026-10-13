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
 * 在调用模型前拦截包含禁用词的请求。
 */
public final class VerifyAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "测试违禁词",
            "虚假广告",
            "恶意链接",
            "诈骗"
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
                "这条消息包含不允许的内容，无法回答");
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
