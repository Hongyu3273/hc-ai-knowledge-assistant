package com.hc.aiagenthyc.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Kind of input a model route accepts, used to reject messages (including history) that the selected model cannot
 * process. Every route must support {@link #TEXT}; {@link #AUDIO} is declared but currently rejected by
 * {@link ModelRoute}.
 */
public enum InputType {
    TEXT, IMAGE, AUDIO;
    /** Lowercase name used in JSON responses and error messages, for example {@code image}. */
    @JsonValue public String value() { return name().toLowerCase(Locale.ROOT); }
}
