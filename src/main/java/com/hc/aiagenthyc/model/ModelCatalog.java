package com.hc.aiagenthyc.model;

import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Read-only registry of enabled chat routes, their input capabilities, and the default route. */
@Component
public class ModelCatalog {
    private final Map<String, ModelRoute> routes;
    private final String defaultModel;
    public ModelCatalog(@Qualifier("loveAppModelClients") Map<String, ModelRoute> routes,
                        @Value("${app.love.model:dashscope}") String defaultModel) {
        this.routes = Map.copyOf(routes);
        this.defaultModel = defaultModel;
        // Fail at startup if configuration selects a route that was never registered.
        require(defaultModel);
    }
    public String resolve(String requested) {
        // Only an omitted model uses the default; unknown IDs fail rather than silently falling back.
        String id = requested == null ? defaultModel : requested;
        require(id);
        return id;
    }
    public ModelRoute require(String id) {
        ModelRoute route = id == null ? null : routes.get(id);
        if (route == null) throw new ChatException(ErrorCode.MODEL_NOT_AVAILABLE,
                "The model is disabled or does not exist; available models: " + routes.keySet());
        return route;
    }
    public Map<String, ModelRoute> routes() { return routes; }
    public CatalogView view() {
        // Expose IDs and capabilities to clients without exposing provider clients or credentials.
        return new CatalogView(defaultModel, routes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new ModelView(e.getKey(), e.getValue().supportedInputs())).toList());
    }
    public record ModelView(String id, Set<InputType> supportedInputs) { }
    public record CatalogView(String defaultModel, List<ModelView> models) { }
}
