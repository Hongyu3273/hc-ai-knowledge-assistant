package com.hc.aiagenthyc.attachment.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.model.StoredAttachment;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import org.springframework.stereotype.Repository;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.regex.Pattern;

@Repository
public class LocalAttachmentStorage implements AttachmentStorage {
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final Path root;
    private final ObjectMapper mapper;
    private final long maxBytes;

    public LocalAttachmentStorage(AttachmentProperties properties, ObjectMapper mapper) {
        this.root = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
        this.mapper = mapper;
        this.maxBytes = properties.getMaxFileBytes();
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) throw new IOException();
        } catch (IOException e) {
            throw new ChatException(ErrorCode.STORAGE_FAILURE, "无法初始化附件存储");
        }
    }

    @Override
    public void save(StoredAttachment metadata, byte[] content) {
        Path destination = directory(metadata.fileId());
        Path staging = root.resolve(".upload-" + UUID.randomUUID());
        try {
            Files.createDirectory(staging);
            Files.write(staging.resolve("content"), content, StandardOpenOption.CREATE_NEW);
            mapper.writeValue(staging.resolve("metadata.json").toFile(), metadata);
            // 完整目录一次发布，不暴露半写入的附件。
            try { Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(staging, destination); }
        } catch (IOException e) {
            throw new ChatException(ErrorCode.STORAGE_FAILURE, "附件保存失败，请重试");
        } finally {
            try {
                Files.deleteIfExists(staging.resolve("content"));
                Files.deleteIfExists(staging.resolve("metadata.json"));
                Files.deleteIfExists(staging);
            } catch (IOException ignored) { /* 不掩盖原始错误；未发布目录不对外可见。 */ }
        }
    }

    @Override
    public StoredAttachment metadata(String fileId) {
        try {
            Path file = regularFile(fileId, "metadata.json");
            if (Files.size(file) > 8192) throw unavailable();
            StoredAttachment metadata = mapper.readValue(file.toFile(), StoredAttachment.class);
            if (!fileId.equals(metadata.fileId()) || metadata.size() <= 0 || metadata.size() > maxBytes
                    || Files.size(regularFile(fileId, "content")) != metadata.size()) throw unavailable();
            return metadata;
        } catch (IOException e) { throw unavailable(); }
    }

    @Override
    public byte[] read(String fileId) {
        StoredAttachment metadata = metadata(fileId);
        try (var input = Files.newInputStream(regularFile(fileId, "content"))) {
            byte[] bytes = input.readNBytes(Math.toIntExact(maxBytes + 1));
            if (bytes.length != metadata.size()) throw unavailable();
            return bytes;
        } catch (IOException e) { throw unavailable(); }
    }

    private Path directory(String id) {
        if (id == null || !ID.matcher(id).matches()) throw unavailable();
        Path path = root.resolve(id);
        if (Files.isSymbolicLink(path)) throw unavailable();
        return path;
    }

    private Path regularFile(String id, String name) {
        Path file = directory(id).resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
        return file;
    }

    private static ChatException unavailable() {
        return new ChatException(ErrorCode.ATTACHMENT_UNAVAILABLE, "附件不存在或不可用，请重新上传");
    }
}
