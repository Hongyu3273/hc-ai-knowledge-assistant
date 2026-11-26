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

/**
 * Stores each attachment on the local file system as {@code <directory>/<fileId>/content} plus
 * {@code <directory>/<fileId>/metadata.json}.
 * Reads re-validate the ID, reject symbolic links and cross-check the metadata against the content size, so tampered
 * or partial files surface as {@link ErrorCode#ATTACHMENT_UNAVAILABLE} instead of being sent to a model.
 */
@Repository
public class LocalAttachmentStorage implements AttachmentStorage {
    // Only lowercase UUIDs are valid IDs; this keeps client-supplied IDs from containing path separators or "..".
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final Path root;
    private final ObjectMapper mapper;
    private final long maxBytes;

    /**
     * Creates the storage root if needed.
     *
     * @throws ChatException with {@link ErrorCode#STORAGE_FAILURE} if the root cannot be created, is not a directory
     *                       or is a symbolic link
     */
    public LocalAttachmentStorage(AttachmentProperties properties, ObjectMapper mapper) {
        this.root = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
        this.mapper = mapper;
        this.maxBytes = properties.getMaxFileBytes();
        try {
            Files.createDirectories(root);
            // A symbolic-link root could redirect attachment writes outside the configured directory.
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) throw new IOException();
        } catch (IOException e) {
            throw new ChatException(ErrorCode.STORAGE_FAILURE, "Unable to initialize attachment storage");
        }
    }

    @Override
    public void save(StoredAttachment metadata, byte[] content) {
        Path destination = directory(metadata.fileId());
        // The ".upload-" prefix cannot match the ID pattern, so readers never resolve a staging directory.
        Path staging = root.resolve(".upload-" + UUID.randomUUID());
        try {
            Files.createDirectory(staging);
            Files.write(staging.resolve("content"), content, StandardOpenOption.CREATE_NEW);
            mapper.writeValue(staging.resolve("metadata.json").toFile(), metadata);
            // Publish the complete directory so readers never see a partially written attachment.
            try { Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(staging, destination); }
        } catch (IOException e) {
            throw new ChatException(ErrorCode.STORAGE_FAILURE, "Failed to save the attachment. Please try again.");
        } finally {
            try {
                Files.deleteIfExists(staging.resolve("content"));
                Files.deleteIfExists(staging.resolve("metadata.json"));
                Files.deleteIfExists(staging);
            } catch (IOException ignored) { /* Preserve the original failure; unpublished directories are not exposed to readers. */ }
        }
    }

    @Override
    public StoredAttachment metadata(String fileId) {
        try {
            Path file = regularFile(fileId, "metadata.json");
            // Metadata is small; refuse oversized files before parsing them.
            if (Files.size(file) > 8192) throw unavailable();
            StoredAttachment metadata = mapper.readValue(file.toFile(), StoredAttachment.class);
            // Treat inconsistent metadata as missing so a swapped or truncated file is never served.
            if (!fileId.equals(metadata.fileId()) || metadata.size() <= 0 || metadata.size() > maxBytes
                    || Files.size(regularFile(fileId, "content")) != metadata.size()) throw unavailable();
            return metadata;
        } catch (IOException e) { throw unavailable(); }
    }

    @Override
    public byte[] read(String fileId) {
        StoredAttachment metadata = metadata(fileId);
        try (var input = Files.newInputStream(regularFile(fileId, "content"))) {
            // Bounded read: the file may have changed since the metadata check, so never read more than the limit.
            byte[] bytes = input.readNBytes(Math.toIntExact(maxBytes + 1));
            if (bytes.length != metadata.size()) throw unavailable();
            return bytes;
        } catch (IOException e) { throw unavailable(); }
    }

    /** Resolves the attachment directory after validating the ID and rejecting a symbolic-link directory. */
    private Path directory(String id) {
        if (id == null || !ID.matcher(id).matches()) throw unavailable();
        Path path = root.resolve(id);
        if (Files.isSymbolicLink(path)) throw unavailable();
        return path;
    }

    /** Resolves a file inside the attachment directory; it must be a regular file and not a symbolic link. */
    private Path regularFile(String id, String name) {
        Path file = directory(id).resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
        return file;
    }

    private static ChatException unavailable() {
        return new ChatException(ErrorCode.ATTACHMENT_UNAVAILABLE, "The attachment does not exist or is unavailable. Please upload it again.");
    }
}
