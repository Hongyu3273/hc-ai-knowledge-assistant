package com.hc.aiagenthyc.attachment.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.Set;

/**
 * Binds and validates the {@code app.attachments.*} settings that limit image uploads and the images sent to a model.
 * Invalid values fail application startup through bean validation.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.attachments")
public class AttachmentProperties {
    /** Root directory for uploaded attachments. Default: {@code ./tmp/chat-attachments}. */
    @NotBlank private String directory = "./tmp/chat-attachments";
    /** Maximum size of one uploaded file in bytes, capped at 10 MiB. Default: 10 MiB. */
    @Min(1) @Max(10485760) private long maxFileBytes = 10 * 1024 * 1024;
    /** Maximum width x height of one image, checked before full decoding. Default: 16,000,000. */
    @Min(1) @Max(16000000) private long maxPixels = 16_000_000;
    /** Maximum number of attachments in one chat message; currently fixed at 1. Default: 1. */
    @Min(1) @Max(1) private int maxPerMessage = 1;
    /**
     * Maximum total bytes of all images loaded from one conversation's history plus the new message
     * for a single model call. Default: 30 MiB.
     */
    @Min(1) @Max(104857600) private long maxContextBytes = 30 * 1024 * 1024;
    /** MIME types accepted on upload and when replaying history. Default: JPEG, PNG and WebP. */
    @NotEmpty private Set<String> allowedTypes = Set.of("image/jpeg", "image/png", "image/webp");
}
