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
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.abort;
import static com.hc.aiagenthyc.support.ImageFixtures.image;

/**
 * Unit test of {@link AttachmentService} and {@link LocalAttachmentStorage} on a temporary directory: format detection
 * from the bytes, size and pixel limits, path safety, and persistence across a storage restart. Images are generated
 * by {@code ImageFixtures}; WebP decoding relies on the TwelveMonkeys ImageIO plugin on the classpath.
 */
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
        // The filename and declared content type are both wrong on purpose; only the bytes decide the type.
        var metadata = service.upload(new MockMultipartFile("file", "../../wrong.jpg", "audio/wav", bytes));
        assertThat(metadata.mimeType()).isEqualTo("image/png");
        // Path segments are stripped from the stored name.
        assertThat(metadata.originalFilename()).isEqualTo("wrong.jpg");
        assertThat(metadata.width()).isEqualTo(3);
        // A new storage instance over the same directory simulates an application restart.
        var reopened = new LocalAttachmentStorage(properties, new ObjectMapper());
        assertThat(reopened.metadata(metadata.fileId())).isEqualTo(metadata);
        assertThat(reopened.read(metadata.fileId())).isEqualTo(bytes);
    }

    @Test void acceptsJpegAndWebp() throws Exception {
        assertThat(service.upload(new MockMultipartFile("file", image("jpg", 2, 2))).mimeType()).isEqualTo("image/jpeg");
        // A minimal 1x1 lossy WebP; ImageIO cannot write WebP, so it is embedded rather than generated.
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        assertThat(service.upload(new MockMultipartFile("file", webp)).mimeType()).isEqualTo("image/webp");
    }

    @Test void rejectsSpoofedImagesAudioAndEmptyFiles() {
        // Claims to be a PNG, but no ImageIO reader recognises the bytes.
        assertCode(() -> service.upload(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1,2,3})),
                ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE);
        // A real WAV header declared as audio: audio input is not supported, whatever the declared type says.
        assertCode(() -> service.upload(new MockMultipartFile("file", "voice.wav", "audio/wav", wavHeader())),
                ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE);
        assertCode(() -> service.upload(new MockMultipartFile("file", new byte[0])), ErrorCode.INVALID_ATTACHMENT);
    }

    @Test void rejectsByteAndPixelLimits() throws Exception {
        byte[] bytes = image("png", 3, 3);
        properties.setMaxFileBytes(bytes.length - 1);
        assertCode(() -> service.upload(new MockMultipartFile("file", bytes)), ErrorCode.ATTACHMENT_TOO_LARGE);
        properties.setMaxFileBytes(10000);
        // A 3x3 image has 9 pixels, one over the limit.
        properties.setMaxPixels(8);
        assertCode(() -> service.upload(new MockMultipartFile("file", bytes)), ErrorCode.ATTACHMENT_TOO_LARGE);
    }

    @Test void rejectsTraversalAndMissingFiles() throws Exception {
        // IDs must be UUIDs, so a path is rejected before any file is touched.
        assertCode(() -> service.read("../../pom.xml"), ErrorCode.ATTACHMENT_UNAVAILABLE);
        assertCode(() -> service.read(UUID.randomUUID().toString()), ErrorCode.ATTACHMENT_UNAVAILABLE);
        var saved = service.upload(new MockMultipartFile("file", image("png", 1, 1)));
        Files.delete(directory.resolve(saved.fileId()).resolve("content"));
        assertCode(() -> service.read(saved.fileId()), ErrorCode.ATTACHMENT_UNAVAILABLE);
    }

    @Test void rejectsSymbolicLinks() throws Exception {
        var saved = service.upload(new MockMultipartFile("file", image("png", 1, 1)));
        Path content = directory.resolve(saved.fileId()).resolve("content");
        Files.delete(content);
        // Replace the content file with a link; storage reads only regular files and does not follow links.
        try {
            Files.createSymbolicLink(content, directory.resolve(saved.fileId()).resolve("metadata.json"));
        } catch (FileSystemException | UnsupportedOperationException e) {
            // Windows allows symbolic links only with Developer Mode or administrator rights; skip rather than fail.
            abort("Cannot create symbolic links here: " + e.getMessage());
        }
        assertCode(() -> service.read(saved.fileId()), ErrorCode.ATTACHMENT_UNAVAILABLE);
    }

    @Test void rejectsCorruptImageWithoutPublishingAttachment() throws Exception {
        byte[] bytes = image("png", 3, 3);
        // The first 40 bytes keep a valid PNG header, so the format is detected but decoding the pixels fails.
        assertCode(() -> service.upload(new MockMultipartFile("file", java.util.Arrays.copyOf(bytes, 40))),
                ErrorCode.INVALID_ATTACHMENT);
        // Decoding fails before storage is called, so nothing is written to the attachment directory.
        try (var files = Files.list(directory)) { assertThat(files.toList()).isEmpty(); }
    }

    /** A 44-byte header of an empty 8 kHz mono 16-bit PCM WAV file. */
    private static byte[] wavHeader() {
        return java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36)
                .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(0)
                .array();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, ErrorCode code) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ChatException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
