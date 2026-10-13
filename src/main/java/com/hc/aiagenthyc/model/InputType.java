package com.hc.aiagenthyc.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum InputType {
    TEXT, IMAGE, AUDIO;
    @JsonValue public String value() { return name().toLowerCase(Locale.ROOT); }
}
