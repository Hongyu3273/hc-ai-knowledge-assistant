package com.hc.aiagenthyc.attachment.storage;

import com.hc.aiagenthyc.attachment.model.StoredAttachment;

/** 当前实现为本地文件；后续可替换为对象存储，不影响 LoveApp。 */
public interface AttachmentStorage {
    void save(StoredAttachment metadata, byte[] content);
    StoredAttachment metadata(String fileId);
    byte[] read(String fileId);
}
