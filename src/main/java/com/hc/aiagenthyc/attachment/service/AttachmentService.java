package com.hc.aiagenthyc.attachment.service;

import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.model.StoredAttachment;
import com.hc.aiagenthyc.attachment.storage.AttachmentStorage;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * Validates uploaded images and stores them through {@link AttachmentStorage}.
 * Clients upload an image first, then reference its {@code fileId} in chat requests; the multimodal message assembler
 * reads the stored bytes back through this service when it builds a model request.
 */
@Service
public class AttachmentService {
    private final AttachmentStorage storage;
    private final AttachmentProperties properties;
    public AttachmentService(AttachmentStorage storage, AttachmentProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    /**
     * Validates and stores one uploaded image.
     * The file must be non-empty, within {@code maxFileBytes} and {@code maxPixels}, decodable, and detected from its
     * bytes as an allowed JPEG, PNG or WebP image.
     *
     * @param file multipart file from the upload request
     * @return metadata of the stored image, including the new {@code fileId}
     * @throws ChatException with {@link ErrorCode#INVALID_ATTACHMENT}, {@link ErrorCode#ATTACHMENT_TOO_LARGE},
     *                       {@link ErrorCode#UNSUPPORTED_ATTACHMENT_TYPE} or {@link ErrorCode#STORAGE_FAILURE}
     */
    public StoredAttachment upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ChatException(ErrorCode.INVALID_ATTACHMENT, "Please select an image file");
        if (file.getSize() > properties.getMaxFileBytes()) throw tooLarge();
        byte[] content;
        try (var input = file.getInputStream()) {
            // Read one byte past the limit to detect oversized streams without reading them in full.
            content = input.readNBytes(Math.toIntExact(properties.getMaxFileBytes() + 1));
        } catch (IOException e) {
            throw new ChatException(ErrorCode.INVALID_ATTACHMENT, "Unable to read the uploaded file");
        }
        if (content.length > properties.getMaxFileBytes()) throw tooLarge();
        // Inspect actual image bytes rather than trusting the filename or uploaded MIME type.
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw unsupported();
            var reader = readers.next();
            try {
                reader.setInput(stream);
                String mime = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    default -> throw unsupported();
                };
                if (!properties.getAllowedTypes().contains(mime)) throw unsupported();
                // Reject excessive dimensions before allocating a fully decoded image.
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > properties.getMaxPixels()) throw tooLarge();
                // Fully decode once so truncated or corrupted images are rejected at upload, not at model-call time.
                if (reader.read(0) == null) throw new IOException();
                var metadata = new StoredAttachment(UUID.randomUUID().toString(), safeName(file.getOriginalFilename()),
                        mime, content.length, width, height);
                storage.save(metadata, content);
                return metadata;
            } finally { reader.dispose(); }
        } catch (ChatException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Image decoders can throw arbitrary runtime exceptions on malformed input; report them as a bad image.
            throw new ChatException(ErrorCode.INVALID_ATTACHMENT, "The image is corrupted or cannot be decoded");
        }
    }

    /**
     * Returns the stored metadata of an attachment.
     *
     * @throws ChatException with {@link ErrorCode#ATTACHMENT_UNAVAILABLE} if the ID is invalid or the files are missing
     */
    public StoredAttachment metadata(String id) { return storage.metadata(id); }
    /**
     * Returns the stored bytes of an attachment.
     *
     * @throws ChatException with {@link ErrorCode#ATTACHMENT_UNAVAILABLE} if the ID is invalid or the files are missing
     */
    public byte[] read(String id) { return storage.read(id); }

    /** Keeps only the last path segment, replaces control characters and limits the name to 128 characters. */
    private static String safeName(String name) {
        if (name == null) return "image";
        String value = name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        return value.isBlank() ? "image" : value.substring(0, Math.min(128, value.length()));
    }
    private static ChatException tooLarge() {
        return new ChatException(ErrorCode.ATTACHMENT_TOO_LARGE, "The image file size or pixel count exceeds the limit");
    }
    private static ChatException unsupported() {
        return new ChatException(ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE, "Only JPEG, PNG, and WebP images are currently supported");
    }
}
