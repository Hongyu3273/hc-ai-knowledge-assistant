package com.hc.aiagenthyc.common.error;

/** Carries a client-safe description without provider responses, API keys, or local paths. */
public final class ChatException extends IllegalArgumentException {
    private final ErrorCode code;
    public ChatException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
    public ErrorCode code() { return code; }
}
