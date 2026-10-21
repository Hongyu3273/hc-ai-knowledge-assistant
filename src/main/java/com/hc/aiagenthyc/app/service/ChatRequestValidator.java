package com.hc.aiagenthyc.app.service;

import com.hc.aiagenthyc.app.model.LoveChatRequest;
import com.hc.aiagenthyc.app.model.LoveReportRequest;
import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;

@Component
public class ChatRequestValidator {
    private final Validator validator;
    private final AttachmentProperties properties;
    public ChatRequestValidator(Validator validator, AttachmentProperties properties) {
        this.validator = validator;
        this.properties = properties;
    }
    public void validate(LoveReportRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new ChatException(ErrorCode.INVALID_REQUEST, "Invalid request fields. Check chatId, model, text, attachments, userName, and relationshipStatus.");
    }

    public void validate(LoveChatRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new ChatException(ErrorCode.INVALID_REQUEST, "Invalid request fields. Check chatId, model, text, and attachments.");
        if (request.chatId().getBytes(StandardCharsets.UTF_8).length > 128)
            throw new ChatException(ErrorCode.INVALID_REQUEST, "chatId must not exceed 128 UTF-8 bytes");
        if (!StringUtils.hasText(request.text()) && request.attachments().isEmpty())
            throw new ChatException(ErrorCode.EMPTY_CHAT_INPUT, "Text and attachments cannot both be empty");
        if (request.attachments().size() > properties.getMaxPerMessage())
            throw new ChatException(ErrorCode.INVALID_REQUEST, "Upload at most one image per message");
    }
}
