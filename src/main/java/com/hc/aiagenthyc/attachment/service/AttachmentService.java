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

@Service
public class AttachmentService {
    private final AttachmentStorage storage;
    private final AttachmentProperties properties;
    public AttachmentService(AttachmentStorage storage, AttachmentProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    public StoredAttachment upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ChatException(ErrorCode.INVALID_ATTACHMENT, "请选择图片文件");
        if (file.getSize() > properties.getMaxFileBytes()) throw tooLarge();
        byte[] content;
        try (var input = file.getInputStream()) {
            content = input.readNBytes(Math.toIntExact(properties.getMaxFileBytes() + 1));
        } catch (IOException e) {
            throw new ChatException(ErrorCode.INVALID_ATTACHMENT, "无法读取上传文件");
        }
        if (content.length > properties.getMaxFileBytes()) throw tooLarge();
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
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > properties.getMaxPixels()) throw tooLarge();
                if (reader.read(0) == null) throw new IOException();
                var metadata = new StoredAttachment(UUID.randomUUID().toString(), safeName(file.getOriginalFilename()),
                        mime, content.length, width, height);
                storage.save(metadata, content);
                return metadata;
            } finally { reader.dispose(); }
        } catch (ChatException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ChatException(ErrorCode.INVALID_ATTACHMENT, "图片损坏或无法解码");
        }
    }

    public StoredAttachment metadata(String id) { return storage.metadata(id); }
    public byte[] read(String id) { return storage.read(id); }

    private static String safeName(String name) {
        if (name == null) return "image";
        String value = name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        return value.isBlank() ? "image" : value.substring(0, Math.min(128, value.length()));
    }
    private static ChatException tooLarge() {
        return new ChatException(ErrorCode.ATTACHMENT_TOO_LARGE, "图片文件大小或像素数量超过限制");
    }
    private static ChatException unsupported() {
        return new ChatException(ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE, "当前仅支持 JPEG、PNG、WebP 图片");
    }
}
