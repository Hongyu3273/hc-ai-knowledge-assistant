package com.hc.aiagenthyc.controller.error;

import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.util.UUID;

/** 新业务接口统一错误契约；不把供应商异常、用户原文或凭据返回给调用方。 */
@RestControllerAdvice
public class ChatExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatExceptionHandler.class);
    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ApiError> business(ChatException e) { return error(e.code(), e.getMessage()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return error(ErrorCode.ATTACHMENT_TOO_LARGE, "上传文件超过大小限制");
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestPartException.class, HttpMediaTypeNotSupportedException.class, MultipartException.class})
    public ResponseEntity<ApiError> invalid(Exception e) {
        return error(ErrorCode.INVALID_REQUEST, "请求格式或参数无效，请检查字段和附件");
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound(Exception e) {
        return error(ErrorCode.RESOURCE_NOT_FOUND, "请求的资源不存在");
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(Exception e) {
        return error(ErrorCode.METHOD_NOT_ALLOWED, "请求方法不受支持");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        String id = UUID.randomUUID().toString();
        log.error("Chat request failed, errorId={}, exceptionType={}", id, e.getClass().getName());
        return ResponseEntity.internalServerError().body(new ApiError(ErrorCode.INTERNAL_ERROR.name(),
                "服务暂时不可用，请稍后重试", id));
    }
    private ResponseEntity<ApiError> error(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(new ApiError(code.name(), message, UUID.randomUUID().toString()));
    }
    public record ApiError(String code, String message, String errorId) { }
}
