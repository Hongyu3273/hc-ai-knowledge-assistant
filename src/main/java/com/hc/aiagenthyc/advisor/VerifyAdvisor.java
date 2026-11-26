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

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rejects requests containing blocked terms before calling the model.
 *
 * <p>By default the latest user message is checked. When a caller augments that message with other material
 * (for example retrieved knowledge-base passages), it should pass the user's own words with
 * {@code advisors(a -> a.param(VerifyAdvisor.CHECKED_TEXT, text))} so that blocked words inside the
 * material do not reject the user's question.</p>
 * <p>Matching is a case-insensitive substring search against a fixed word list; runs of whitespace in the text count
 * as a single space. A match throws (or, for streams,
 * emits) a {@link ChatException} with {@link ErrorCode#CONTENT_NOT_ALLOWED}, so the model is never called.</p>
 */
public final class VerifyAdvisor implements CallAdvisor, StreamAdvisor {

    /** Advisor context key holding the text to check instead of the outgoing user message. */
    public static final String CHECKED_TEXT = "hc.verify.checkedText";

    /** Blocked terms. Entries must be lower case with single spaces, because the text is normalized before matching. */
    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "test prohibited phrase",
            "false advertising",
            "malicious link",
            "fraud"
    );

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final int order;

    /** Creates the advisor with order {@code 0}. */
    public VerifyAdvisor() {
        this(0);
    }

    /** @param order advisor order; lower values run earlier in the chain */
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
        if (containsForbiddenWord(textToCheck(request))) {
            throw refusal();
        }
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        if (containsForbiddenWord(textToCheck(request))) {
            return Flux.error(refusal());
        }
        return chain.nextStream(request);
    }

    /**
     * Applies the same check directly, for callers that want to reject text before doing other work.
     *
     * @param text text to check; {@code null} passes
     * @throws ChatException with {@link ErrorCode#CONTENT_NOT_ALLOWED} when a blocked term is found
     */
    public static void check(String text) {
        if (containsForbiddenWord(text)) {
            throw refusal();
        }
    }

    private static String textToCheck(ChatClientRequest request) {
        Object override = request.context().get(CHECKED_TEXT);
        // A missing or non-String override falls back to the outgoing user message.
        if (override instanceof String text) {
            return text;
        }
        return request.prompt().getUserMessage().getText();
    }

    private static ChatException refusal() {
        return new ChatException(
                ErrorCode.CONTENT_NOT_ALLOWED,
                "This message contains prohibited content and cannot be answered");
    }

    private static boolean containsForbiddenWord(String userText) {
        if (userText == null) {
            return false;
        }
        String normalized = normalize(userText);
        for (String word : FORBIDDEN_WORDS) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lower-cases the text and collapses whitespace so that "FRAUD", "Fraud" or "false\n  advertising" match the
     * lower-case, single-spaced entries in {@link #FORBIDDEN_WORDS}. {@link Locale#ROOT} keeps the result independent
     * of the server's default locale (in Turkish, for example, "I" would otherwise lower-case to a dotless "ı").
     */
    static String normalize(String text) {
        return WHITESPACE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
    }
}
