package com.hc.aiagenthyc.app.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LoveChatRequest(
        @NotBlank @Size(max = 128) String chatId,
        @Size(max = 128) String model,
        @Size(max = 20000) String text,
        List<@NotNull @Valid ChatAttachment> attachments) {
    public LoveChatRequest {
        attachments = attachments == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(attachments));
    }
}
