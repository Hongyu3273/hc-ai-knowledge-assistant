package com.hc.aiagenthyc.attachment.model;

/** File paths are resolved inside the storage implementation and are never returned to clients. */
public record StoredAttachment(String fileId, String originalFilename, String mimeType,
                               long size, int width, int height) {
}
