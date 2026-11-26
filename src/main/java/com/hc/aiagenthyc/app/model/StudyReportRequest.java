package com.hc.aiagenthyc.app.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request for a structured {@link StudyReport}: the chat-turn fields of {@link AssistantChatRequest} plus optional
 * personalisation for the report-instruction prompt.
 *
 * @param chatId      conversation ID; required, at most 128 characters
 * @param model       model route ID; {@code null} selects the configured default model
 * @param text        user message, at most 20,000 characters
 * @param attachments uploaded file references; {@code null} becomes an empty list, and the list is copied unmodifiable
 * @param userName    name used in the report prompt, at most 64 characters; blank uses a default
 * @param focusArea   topic to focus the report on, at most 64 characters; blank uses a default
 */
public record StudyReportRequest(
        @NotBlank @Size(max = 128) String chatId,
        @Size(max = 128) String model,
        @Size(max = 20000) String text,
        List<@NotNull @Valid ChatAttachment> attachments,
        @Size(max = 64) String userName,
        @Size(max = 64) String focusArea) {
    public StudyReportRequest {
        attachments = attachments == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(attachments));
    }

    /** Returns the chat-turn part of this request, without the report personalisation fields. */
    public AssistantChatRequest chatRequest() {
        return new AssistantChatRequest(chatId, model, text, attachments);
    }
}
