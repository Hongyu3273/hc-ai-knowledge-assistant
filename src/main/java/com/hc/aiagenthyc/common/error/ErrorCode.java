package com.hc.aiagenthyc.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes returned in the {@code code} field of API error responses, each mapped to an HTTP status.
 * Clients should branch on the code name rather than on the message text.
 */
public enum ErrorCode {
    /** Malformed body, failed bean validation or an invalid field value (400). */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    /** No handler or static resource exists for the requested path (404). */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** The path exists but does not support the HTTP method (405). */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    /** A chat message has neither text nor attachments (400). */
    EMPTY_CHAT_INPUT(HttpStatus.BAD_REQUEST),
    /** The requested model route is not registered, or an optional feature such as cloud RAG is disabled (400). */
    MODEL_NOT_AVAILABLE(HttpStatus.BAD_REQUEST),
    /** The selected model does not accept an input type in the message or its history, such as images (400). */
    MODEL_INPUT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST),
    /** The upload or a history attachment is not an allowed image type (400). */
    UNSUPPORTED_ATTACHMENT_TYPE(HttpStatus.BAD_REQUEST),
    /** The upload is missing, unreadable or cannot be decoded as an image (400). */
    INVALID_ATTACHMENT(HttpStatus.BAD_REQUEST),
    /** A file exceeds the size or pixel limit, or a conversation exceeds the total image-size limit (413). */
    ATTACHMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    /** A referenced attachment ID is invalid, missing or fails its integrity checks (404). */
    ATTACHMENT_UNAVAILABLE(HttpStatus.NOT_FOUND),
    /** The user's text contains a term blocked by {@code VerifyAdvisor} (400). */
    CONTENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
    /** The model or RAG retrieval call failed, or the model returned an empty or unparseable answer (502). */
    MODEL_CALL_FAILED(HttpStatus.BAD_GATEWAY),
    /** Attachment storage could not be initialized or written (500). */
    STORAGE_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR),
    /** Any unexpected exception; the response carries an error ID that is also logged (500). */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
    /** Returns the HTTP status sent with this code. */
    public HttpStatus status() { return status; }
}
