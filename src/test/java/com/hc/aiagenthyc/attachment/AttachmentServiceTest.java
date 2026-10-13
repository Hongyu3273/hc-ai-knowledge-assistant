package com.hc.aiagenthyc.attachment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import com.hc.aiagenthyc.attachment.storage.LocalAttachmentStorage;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static com.hc.aiagenthyc.support.ImageFixtures.image;

class AttachmentServiceTest {
    @TempDir Path directory;
    private AttachmentProperties properties;
    private AttachmentService service;
    private LocalAttachmentStorage storage;
    @BeforeEach void setUp() {
        properties = new AttachmentProperties();
        properties.setDirectory(directory.toString());
        storage = new LocalAttachmentStorage(properties, new ObjectMapper());
        service = new AttachmentService(storage, properties);
    }

    @Test void detectsActualFormatAndPersistsAcrossRestart() throws Exception {
        byte[] bytes = image("png", 3, 2);
        var metadata = service.upload(new MockMultipartFile("file", "../../wrong.jpg", "audio/wav", bytes));
        assertThat(metadata.mimeType()).isEqualTo("image/png");
        assertThat(metadata.originalFilename()).isEqualTo("wrong.jpg");
        assertThat(metadata.width()).isEqualTo(3);
        var reopened = new LocalAttachmentStorage(properties, new ObjectMapper());
        assertThat(reopened.metadata(metadata.fileId())).isEqualTo(metadata);
        assertThat(reopened.read(metadata.fileId())).isEqualTo(bytes);
    }

    @Test void acceptsJpegAndWebp() throws Exception {
        assertThat(service.upload(new MockMultipartFile("file", image("jpg", 2, 2))).mimeType()).isEqualTo("image/jpeg");
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        assertThat(service.upload(new MockMultipartFile("file", webp)).mimeType()).isEqualTo("image/webp");
    }

    @Test void rejectsSpoofedImagesAudioAndEmptyFiles() {
        assertCode(() -> service.upload(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1,2,3})),
                ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE);
        assertCode(() -> service.upload(new MockMultipartFile("file", new byte[0])), ErrorCode.INVALID_ATTACHMENT);
    }

    @Test void rejectsByteAndPixelLimits() throws Exception {
        byte[] bytes = image("png", 3, 3);
        properties.setMaxFileBytes(bytes.length - 1);
        assertCode(() -> service.upload(new MockMultipartFile("file", bytes)), ErrorCode.ATTACHMENT_TOO_LARGE);
        properties.setMaxFileBytes(10000);
        properties.setMaxPixels(8);
        assertCode(() -> service.upload(new MockMultipartFile("file", bytes)), ErrorCode.ATTACHMENT_TOO_LARGE);
    }

    @Test void rejectsTraversalMissingFilesAndSymbolicLinks() throws Exception {
        assertCode(() -> service.read("../../pom.xml"), ErrorCode.ATTACHMENT_UNAVAILABLE);
        assertCode(() -> service.read(UUID.randomUUID().toString()), ErrorCode.ATTACHMENT_UNAVAILABLE);
        var saved = service.upload(new MockMultipartFile("file", image("png", 1, 1)));
        Path content = directory.resolve(saved.fileId()).resolve("content");
        Files.delete(content);
        Files.createSymbolicLink(content, directory.resolve(saved.fileId()).resolve("metadata.json"));
        assertCode(() -> service.read(saved.fileId()), ErrorCode.ATTACHMENT_UNAVAILABLE);
    }

    @Test void rejectsCorruptImageWithoutPublishingAttachment() throws Exception {
        byte[] bytes = image("png", 3, 3);
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", java.util.Arrays.copyOf(bytes, 40))))
                .isInstanceOf(ChatException.class);
        try (var files = Files.list(directory)) { assertThat(files.toList()).isEmpty(); }
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, ErrorCode code) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ChatException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
