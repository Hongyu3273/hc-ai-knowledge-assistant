package com.hc.aiagenthyc.config;

import com.hc.aiagenthyc.model.InputType;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.gemini")
public class GeminiProperties {
    private Set<InputType> proInputs = Set.of(
            InputType.TEXT, InputType.IMAGE);
    private Set<InputType> flashLiteInputs = Set.of(
            InputType.TEXT, InputType.IMAGE);
    private String apiKey;
    private String proModel = "gemini-2.5-pro";
    private String flashLiteModel = "gemini-2.5-flash-lite";
    private int timeoutMs = 120_000;
}
