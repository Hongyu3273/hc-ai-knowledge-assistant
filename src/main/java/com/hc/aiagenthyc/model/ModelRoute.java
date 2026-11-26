package com.hc.aiagenthyc.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import java.util.Set;

/**
 * A chat client plus the provider model name to request and the input types the route accepts.
 *
 * <p>A client may serve multiple model IDs; a null model uses the provider-configured default.</p>
 *
 * @param client          provider chat client; required
 * @param model           provider model name sent in the request options, or {@code null} for the client default
 * @param supportedInputs accepted input types; must contain {@link InputType#TEXT} and must not contain
 *                        {@link InputType#AUDIO}; copied into an immutable set
 */
public record ModelRoute(ChatClient client, String model, Set<InputType> supportedInputs) {
    /**
     * @throws NullPointerException     if {@code client} or {@code supportedInputs} is null
     * @throws IllegalArgumentException if the inputs omit text or include audio
     */
    public ModelRoute {
        java.util.Objects.requireNonNull(client, "client cannot be null");
        supportedInputs = Set.copyOf(supportedInputs);
        if (!supportedInputs.contains(InputType.TEXT) || supportedInputs.contains(InputType.AUDIO)) {
            throw new IllegalArgumentException("This application supports text or text + image input; audio is not yet available");
        }
    }
    /** Creates a text-only route. */
    public ModelRoute(ChatClient client, String model) {
        this(client, model, Set.of(InputType.TEXT));
    }
    /** Starts a new request on the client, with the route's model name set when one is configured. */
    public ChatClient.ChatClientRequestSpec prompt() {
        // Set options on a new request so shared clients can serve different model IDs safely.
        var request = client.prompt();
        return model == null ? request : request.options(ChatOptions.builder().model(model).build());
    }
}
