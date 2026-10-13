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
            throw new ChatException(ErrorCode.INVALID_REQUEST, "请求字段无效，请检查 chatId、model、text、attachments、userName 和 relationshipStatus");
    }

    public void validate(LoveChatRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new ChatException(ErrorCode.INVALID_REQUEST, "请求字段无效，请检查 chatId、model、text 和 attachments");
        if (request.chatId().getBytes(StandardCharsets.UTF_8).length > 128)
            throw new ChatException(ErrorCode.INVALID_REQUEST, "chatId 不能超过 128 个 UTF-8 字节");
        if (!StringUtils.hasText(request.text()) && request.attachments().isEmpty())
            throw new ChatException(ErrorCode.EMPTY_CHAT_INPUT, "文字和附件不能同时为空");
        if (request.attachments().size() > properties.getMaxPerMessage())
            throw new ChatException(ErrorCode.INVALID_REQUEST, "每条消息最多上传一张图片");
    }
}
