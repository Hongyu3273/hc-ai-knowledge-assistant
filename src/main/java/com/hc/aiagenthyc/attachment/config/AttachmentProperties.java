package com.hc.aiagenthyc.attachment.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.Set;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.attachments")
public class AttachmentProperties {
    @NotBlank private String directory = "./tmp/chat-attachments";
    @Min(1) @Max(10485760) private long maxFileBytes = 10 * 1024 * 1024;
    @Min(1) @Max(16000000) private long maxPixels = 16_000_000;
    @Min(1) @Max(1) private int maxPerMessage = 1;
    @Min(1) @Max(104857600) private long maxContextBytes = 30 * 1024 * 1024;
    @NotEmpty private Set<String> allowedTypes = Set.of("image/jpeg", "image/png", "image/webp");
}
