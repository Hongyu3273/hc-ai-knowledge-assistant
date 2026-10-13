package com.hc.aiagenthyc.model;

import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Component
public class ModelCatalog {
    private final Map<String, ModelRoute> routes;
    private final String defaultModel;
    public ModelCatalog(@Qualifier("loveAppModelClients") Map<String, ModelRoute> routes,
                        @Value("${app.love.model:dashscope}") String defaultModel) {
        this.routes = Map.copyOf(routes);
        this.defaultModel = defaultModel;
        require(defaultModel);
    }
    public String resolve(String requested) {
        String id = requested == null ? defaultModel : requested;
        require(id);
        return id;
    }
    public ModelRoute require(String id) {
        ModelRoute route = id == null ? null : routes.get(id);
        if (route == null) throw new ChatException(ErrorCode.MODEL_NOT_AVAILABLE,
                "模型未启用或不存在；可用模型: " + routes.keySet());
        return route;
    }
    public Map<String, ModelRoute> routes() { return routes; }
    public CatalogView view() {
        return new CatalogView(defaultModel, routes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new ModelView(e.getKey(), e.getValue().supportedInputs())).toList());
    }
    public record ModelView(String id, Set<InputType> supportedInputs) { }
    public record CatalogView(String defaultModel, List<ModelView> models) { }
}
