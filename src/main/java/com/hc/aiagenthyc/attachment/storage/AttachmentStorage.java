package com.hc.aiagenthyc.attachment.storage;

import com.hc.aiagenthyc.attachment.model.StoredAttachment;

/**
 * Persists validated attachment bytes and metadata by {@code fileId}.
 * The local-file implementation can be replaced with object storage without changing KnowledgeAssistant.
 */
public interface AttachmentStorage {
    /** Stores the content and metadata under {@code metadata.fileId()}; callers pass already validated content. */
    void save(StoredAttachment metadata, byte[] content);
    /** Returns the stored metadata, or throws a {@code ChatException} if the attachment is missing or invalid. */
    StoredAttachment metadata(String fileId);
    /** Returns the stored content, or throws a {@code ChatException} if the attachment is missing or invalid. */
    byte[] read(String fileId);
}
