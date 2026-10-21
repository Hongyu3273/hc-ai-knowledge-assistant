package com.hc.aiagenthyc.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import java.util.Set;

/** A client may serve multiple model IDs; a null model uses the provider-configured default. */
public record ModelRoute(ChatClient client, String model, Set<InputType> supportedInputs) {
    public ModelRoute {
        java.util.Objects.requireNonNull(client, "client cannot be null");
        supportedInputs = Set.copyOf(supportedInputs);
        if (!supportedInputs.contains(InputType.TEXT) || supportedInputs.contains(InputType.AUDIO)) {
            throw new IllegalArgumentException("This application supports text or text + image input; audio is not yet available");
        }
    }
    public ModelRoute(ChatClient client, String model) {
        this(client, model, Set.of(InputType.TEXT));
    }
    public ChatClient.ChatClientRequestSpec prompt() {
        // Set options on a new request so shared clients can serve different model IDs safely.
        var request = client.prompt();
        return model == null ? request : request.options(ChatOptions.builder().model(model).build());
    }
}
