package com.hc.aiagenthyc.app.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One chat turn sent to {@code KnowledgeAssistant}: optional text plus optional references to uploaded images.
 *
 * <p>Bean Validation annotations are checked by {@code ChatRequestValidator}, which also requires at least text or
 * one attachment and limits the number of attachments per message.</p>
 *
 * @param chatId      conversation ID used as the chat memory key; required, at most 128 characters and 128 UTF-8
 *                    bytes
 * @param model       model route ID; {@code null} selects the configured default model
 * @param text        user message, at most 20,000 characters; may be blank when an attachment is present
 * @param attachments uploaded file references; {@code null} becomes an empty list, and the list is copied unmodifiable
 */
public record AssistantChatRequest(
        @NotBlank @Size(max = 128) String chatId,
        @Size(max = 128) String model,
        @Size(max = 20000) String text,
        List<@NotNull @Valid ChatAttachment> attachments) {
    public AssistantChatRequest {
        attachments = attachments == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(attachments));
    }
}
