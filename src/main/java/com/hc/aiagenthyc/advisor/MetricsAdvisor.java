package com.hc.aiagenthyc.advisor;

import com.hc.aiagenthyc.common.error.ChatException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

/**
 * Records latency, outcome, and token usage for each model call made through a business {@code ChatClient}.
 *
 * <p>Meters (all tagged with {@code operation} and {@code model}):</p>
 * <ul>
 *   <li>{@code hc.ai.model.calls} – timer, also tagged with {@code outcome} (success or an error code)</li>
 *   <li>{@code hc.ai.model.tokens} – counter, also tagged with {@code type} (prompt or completion)</li>
 * </ul>
 * <p>No message text is recorded. Callers set the operation with
 * {@code advisors(a -> a.param(MetricsAdvisor.OPERATION, "rag"))}; it defaults to {@code chat}.</p>
 * <p>Only blocking calls are measured; this advisor does not implement {@code StreamAdvisor}.</p>
 */
public final class MetricsAdvisor implements CallAdvisor {

    /** Advisor context key naming the business operation, such as chat, report, rag, or rag_cloud. */
    public static final String OPERATION = "hc.metrics.operation";

    private final MeterRegistry registry;
    private final String defaultModel;

    /**
     * @param registry     meter registry
     * @param defaultModel tag used when neither the request options nor the response name a model
     */
    public MetricsAdvisor(MeterRegistry registry, String defaultModel) {
        this.registry = registry;
        this.defaultModel = StringUtils.hasText(defaultModel) ? defaultModel : "unknown";
    }

    @Override
    public String getName() {
        return MetricsAdvisor.class.getSimpleName();
    }

    /** Runs before other advisors so the timer covers validation and retrieval advisors too. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /**
     * Times the rest of the advisor chain and the model call, then records the timer and token counters.
     * Exceptions are rethrown unchanged; a {@link ChatException} is tagged with its error code, any other
     * runtime exception with {@code error}.
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String operation = operation(request);
        String requestedModel = requestedModel(request);
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        ChatClientResponse response = null;
        try {
            response = chain.nextCall(request);
            return response;
        } catch (ChatException e) {
            outcome = e.code().name();
            throw e;
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            // Prefer the model name reported by the provider; it can differ from the requested alias.
            ChatResponse chatResponse = response == null ? null : response.chatResponse();
            String model = responseModel(chatResponse, requestedModel);
            sample.stop(Timer.builder("hc.ai.model.calls")
                    .description("Model calls made through business chat clients")
                    .tag("operation", operation)
                    .tag("model", model)
                    .tag("outcome", outcome)
                    .register(registry));
            recordUsage(chatResponse, operation, model);
        }
    }

    private void recordUsage(ChatResponse chatResponse, String operation, String model) {
        if (chatResponse == null || chatResponse.getMetadata() == null) return;
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) return;
        increment(operation, model, "prompt", usage.getPromptTokens());
        increment(operation, model, "completion", usage.getCompletionTokens());
    }

    // Providers that do not report usage return null or zero; skip them so counters only reflect reported tokens.
    private void increment(String operation, String model, String type, Integer tokens) {
        if (tokens == null || tokens <= 0) return;
        Counter.builder("hc.ai.model.tokens")
                .description("Tokens reported by the model provider")
                .tag("operation", operation)
                .tag("model", model)
                .tag("type", type)
                .register(registry)
                .increment(tokens);
    }

    private static String operation(ChatClientRequest request) {
        Object value = request.context().get(OPERATION);
        return value instanceof String text && StringUtils.hasText(text) ? text : "chat";
    }

    private String requestedModel(ChatClientRequest request) {
        var options = request.prompt().getOptions();
        String model = options == null ? null : options.getModel();
        return StringUtils.hasText(model) ? model : defaultModel;
    }

    private static String responseModel(ChatResponse chatResponse, String fallback) {
        if (chatResponse != null && chatResponse.getMetadata() != null
                && StringUtils.hasText(chatResponse.getMetadata().getModel())) {
            return chatResponse.getMetadata().getModel();
        }
        return fallback;
    }
}
