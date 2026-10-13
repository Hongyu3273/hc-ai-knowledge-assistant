package com.hc.aiagenthyc.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    EMPTY_CHAT_INPUT(HttpStatus.BAD_REQUEST),
    MODEL_NOT_AVAILABLE(HttpStatus.BAD_REQUEST),
    MODEL_INPUT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_ATTACHMENT_TYPE(HttpStatus.BAD_REQUEST),
    INVALID_ATTACHMENT(HttpStatus.BAD_REQUEST),
    ATTACHMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    ATTACHMENT_UNAVAILABLE(HttpStatus.NOT_FOUND),
    CONTENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
    MODEL_CALL_FAILED(HttpStatus.BAD_GATEWAY),
    STORAGE_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
    public HttpStatus status() { return status; }
}
