package com.hc.aiagenthyc.attachment.storage;

import com.hc.aiagenthyc.attachment.model.StoredAttachment;

/** The local-file implementation can be replaced with object storage without changing LoveApp. */
public interface AttachmentStorage {
    void save(StoredAttachment metadata, byte[] content);
    StoredAttachment metadata(String fileId);
    byte[] read(String fileId);
}
