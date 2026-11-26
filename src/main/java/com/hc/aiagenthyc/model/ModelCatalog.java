package com.hc.aiagenthyc.model;

import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Read-only registry of enabled chat routes, their input capabilities, and the default route.
 *
 * <p>Routes come from the {@code assistantModelClients} bean, keyed by the public model ID that clients send in
 * requests. {@code KnowledgeAssistant} wraps these routes with its own configured clients.</p>
 */
@Component
public class ModelCatalog {
    private final Map<String, ModelRoute> routes;
    private final String defaultModel;
    /**
     * @param routes       enabled routes by model ID; copied into an immutable map
     * @param defaultModel {@code app.assistant.model}, default {@code dashscope}; must be one of the route IDs
     * @throws ChatException {@link ErrorCode#MODEL_NOT_AVAILABLE} if the default model is not registered
     */
    public ModelCatalog(@Qualifier("assistantModelClients") Map<String, ModelRoute> routes,
                        @Value("${app.assistant.model:dashscope}") String defaultModel) {
        this.routes = Map.copyOf(routes);
        this.defaultModel = defaultModel;
        // Fail at startup if configuration selects a route that was never registered.
        require(defaultModel);
    }
    /**
     * Maps a requested model ID to a registered route ID.
     *
     * @param requested model ID from the request, or {@code null} for the default model
     * @return the route ID to use
     * @throws ChatException {@link ErrorCode#MODEL_NOT_AVAILABLE} if the ID is not registered
     */
    public String resolve(String requested) {
        // Only an omitted model uses the default; unknown IDs fail rather than silently falling back.
        String id = requested == null ? defaultModel : requested;
        require(id);
        return id;
    }
    /**
     * Returns the route for an ID.
     *
     * @throws ChatException {@link ErrorCode#MODEL_NOT_AVAILABLE} if the ID is null or not registered; the message
     *                       lists the available IDs
     */
    public ModelRoute require(String id) {
        ModelRoute route = id == null ? null : routes.get(id);
        if (route == null) throw new ChatException(ErrorCode.MODEL_NOT_AVAILABLE,
                "The model is disabled or does not exist; available models: " + routes.keySet());
        return route;
    }
    /** Returns all routes by model ID (immutable). */
    public Map<String, ModelRoute> routes() { return routes; }
    /** Returns the client-facing catalog: the default model and each model's supported inputs, sorted by ID. */
    public CatalogView view() {
        // Expose IDs and capabilities to clients without exposing provider clients or credentials.
        return new CatalogView(defaultModel, routes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new ModelView(e.getKey(), e.getValue().supportedInputs())).toList());
    }
    /** Public description of one model route: its ID and accepted input types. */
    public record ModelView(String id, Set<InputType> supportedInputs) { }
    /** Response body of the model listing endpoint. */
    public record CatalogView(String defaultModel, List<ModelView> models) { }
}
