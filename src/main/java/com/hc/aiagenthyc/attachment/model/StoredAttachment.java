package com.hc.aiagenthyc.attachment.model;

/** 文件路径由存储实现内部解析，永不返回客户端。 */
public record StoredAttachment(String fileId, String originalFilename, String mimeType,
                               long size, int width, int height) {
}
