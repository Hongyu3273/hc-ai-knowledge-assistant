package com.hc.aiagenthyc.config;

import com.hc.aiagenthyc.model.InputType;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds app.gemini settings; route IDs stay stable even when provider model IDs change.
 * Registered only by {@link GeminiConfiguration}, so these values are read only when {@code app.gemini.enabled=true}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.gemini")
public class GeminiProperties {
    /** Input types advertised and accepted for the {@code gemini-2.5-pro} route. Default: text and image. */
    private Set<InputType> proInputs = Set.of(
            InputType.TEXT, InputType.IMAGE);
    /** Input types advertised and accepted for the {@code gemini-2.5-flash-lite} route. Default: text and image. */
    private Set<InputType> flashLiteInputs = Set.of(
            InputType.TEXT, InputType.IMAGE);
    /** Gemini Developer API key; required when Gemini is enabled. No default. */
    private String apiKey;
    /** Provider model ID sent for the {@code gemini-2.5-pro} route. Default: {@code gemini-2.5-pro}. */
    private String proModel = "gemini-2.5-pro";
    /** Provider model ID sent for the {@code gemini-2.5-flash-lite} route. Default: {@code gemini-2.5-flash-lite}. */
    private String flashLiteModel = "gemini-2.5-flash-lite";
    /** HTTP timeout for Gemini API calls in milliseconds; must be positive. Default: 120000. */
    private int timeoutMs = 120_000;
}
