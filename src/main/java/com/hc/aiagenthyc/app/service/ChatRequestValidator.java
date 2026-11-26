package com.hc.aiagenthyc.app.service;

import com.hc.aiagenthyc.app.model.AssistantChatRequest;
import com.hc.aiagenthyc.app.model.StudyReportRequest;
import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;

/**
 * Validates chat and report requests before any memory access or model call.
 *
 * <p>Runs Bean Validation on the request record, then the rules that annotations cannot express. Every failure is a
 * {@link ChatException}, so the HTTP layer returns a consistent error code. {@code KnowledgeAssistant} calls it itself,
 * so requests built internally or by callers that skip controller {@code @Valid} are checked too.</p>
 */
@Component
public class ChatRequestValidator {
    private final Validator validator;
    private final AttachmentProperties properties;
    public ChatRequestValidator(Validator validator, AttachmentProperties properties) {
        this.validator = validator;
        this.properties = properties;
    }
    /**
     * Checks only the annotations on the report request; callers also validate
     * {@link StudyReportRequest#chatRequest()}.
     *
     * @throws ChatException {@link ErrorCode#INVALID_REQUEST} if the request is null or a constraint fails
     */
    public void validate(StudyReportRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new ChatException(ErrorCode.INVALID_REQUEST, "Invalid request fields. Check chatId, model, text, attachments, userName, and focusArea.");
    }

    /**
     * Checks a chat turn: annotations, the chatId byte limit, non-empty input and the attachment count.
     *
     * @throws ChatException {@link ErrorCode#INVALID_REQUEST} for invalid fields, a chatId over 128 UTF-8 bytes or too
     *                       many attachments; {@link ErrorCode#EMPTY_CHAT_INPUT} when there is neither text nor an
     *                       attachment
     */
    public void validate(AssistantChatRequest request) {
        if (request == null || !validator.validate(request).isEmpty())
            throw new ChatException(ErrorCode.INVALID_REQUEST, "Invalid request fields. Check chatId, model, text, and attachments.");
        // @Size counts characters; the chat memory repositories limit conversation IDs to 128 UTF-8 bytes.
        if (request.chatId().getBytes(StandardCharsets.UTF_8).length > 128)
            throw new ChatException(ErrorCode.INVALID_REQUEST, "chatId must not exceed 128 UTF-8 bytes");
        if (!StringUtils.hasText(request.text()) && request.attachments().isEmpty())
            throw new ChatException(ErrorCode.EMPTY_CHAT_INPUT, "Text and attachments cannot both be empty");
        // The message text assumes the limit is 1, which AttachmentProperties enforces with @Max(1).
        if (request.attachments().size() > properties.getMaxPerMessage())
            throw new ChatException(ErrorCode.INVALID_REQUEST, "Upload at most one image per message");
    }
}
