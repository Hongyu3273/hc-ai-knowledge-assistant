package com.hc.aiagenthyc.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Reference to an image previously uploaded through the attachment API.
 *
 * @param fileId attachment ID returned by the upload; required, must be a lowercase hyphenated UUID, so arbitrary
 *               strings are rejected at the API boundary before storage lookup
 */
public record ChatAttachment(
        @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String fileId) {
}
