package com.hc.aiagenthyc.attachment.model;

/**
 * Metadata of an uploaded, validated image. It is returned to clients by the upload endpoint and stored next to the
 * file content. File paths are resolved inside the storage implementation and are never returned to clients.
 *
 * @param fileId random UUID that clients use to reference the attachment in chat requests
 * @param originalFilename client-supplied file name with directories and control characters removed, at most 128
 *                         characters; informational only and never used to build a path
 * @param mimeType MIME type detected from the image bytes, not the one declared by the client
 * @param size content length in bytes
 * @param width image width in pixels
 * @param height image height in pixels
 */
public record StoredAttachment(String fileId, String originalFilename, String mimeType,
                               long size, int width, int height) {
}
