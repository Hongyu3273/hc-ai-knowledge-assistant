package com.hc.aiagenthyc.common.error;

/**
 * Business failure with an {@link ErrorCode}; {@code ChatExceptionHandler} turns it into an HTTP error response.
 * Carries a client-safe description without provider responses, API keys, or local paths.
 */
public final class ChatException extends IllegalArgumentException {
    private final ErrorCode code;
    /**
     * @param code error code that selects the HTTP status and the {@code code} field of the response
     * @param message client-safe text returned in the response body
     */
    public ChatException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
    /** Returns the error code of this failure. */
    public ErrorCode code() { return code; }
}
