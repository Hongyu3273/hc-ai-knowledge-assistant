package com.hc.aiagenthyc.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Builds a {@link SimpleVectorStore} and, when a directory is given, saves it so later starts can skip re-embedding.
 *
 * <p>A saved index is reused only when its fingerprint matches: a SHA-256 hash of the format version, the embedding
 * model identifier, and every document's source file, title, and text. Any change, or an unreadable file, triggers a
 * rebuild. The index file is written before the fingerprint file, each through a temporary file, so an interrupted
 * save is detected as a mismatch on the next start.</p>
 */
public final class PersistentVectorIndex {

    static final String INDEX_FILE = "knowledge-index.json";
    static final String FINGERPRINT_FILE = "knowledge-index.sha256";
    // 2: one passage per interview question, with question IDs and bank metadata.
    private static final String FORMAT_VERSION = "2";
    /** Documents per embedding request; DashScope text-embedding-v3/v4 accept at most 10 inputs per call. */
    static final int EMBED_BATCH_SIZE = 10;
    private static final Logger log = LoggerFactory.getLogger(PersistentVectorIndex.class);

    private final EmbeddingModel embeddingModel;
    private final String embeddingModelId;
    private final Path directory;

    /**
     * @param embeddingModel   model used to embed documents and queries
     * @param embeddingModelId identifier of that model; change it to force a rebuild
     * @param directory        where to save the index, or {@code null} to keep it in memory only
     */
    public PersistentVectorIndex(EmbeddingModel embeddingModel, String embeddingModelId, Path directory) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.embeddingModelId = Objects.requireNonNullElse(embeddingModelId, "unknown");
        this.directory = directory;
    }

    /**
     * Returns a store containing {@code documents}, loading the saved index when its fingerprint matches and otherwise
     * embedding all documents in batches of {@link #EMBED_BATCH_SIZE} and saving the result. Failures to read or save
     * the index files are logged and do not fail the call; embedding failures propagate.
     *
     * @param documents passages to index; an empty list returns an empty store without touching the disk
     */
    public SimpleVectorStore loadOrBuild(List<Document> documents) {
        if (documents.isEmpty()) {
            return newStore();
        }
        String fingerprint = fingerprint(documents);
        if (directory != null) {
            SimpleVectorStore saved = loadIfCurrent(fingerprint);
            if (saved != null) {
                return saved;
            }
        }
        SimpleVectorStore store = newStore();
        for (int from = 0; from < documents.size(); from += EMBED_BATCH_SIZE) {
            store.add(documents.subList(from, Math.min(from + EMBED_BATCH_SIZE, documents.size())));
        }
        log.info("Embedded {} knowledge-base passages", documents.size());
        if (directory != null) {
            save(store, fingerprint);
        }
        return store;
    }

    /** Returns the saved store when both files exist and the fingerprint matches; otherwise {@code null}. */
    private SimpleVectorStore loadIfCurrent(String fingerprint) {
        Path indexFile = directory.resolve(INDEX_FILE);
        Path fingerprintFile = directory.resolve(FINGERPRINT_FILE);
        if (!Files.isRegularFile(indexFile) || !Files.isRegularFile(fingerprintFile)) {
            return null;
        }
        try {
            if (!fingerprint.equals(Files.readString(fingerprintFile, StandardCharsets.UTF_8).strip())) {
                log.info("Knowledge-base documents or embedding model changed; rebuilding the vector index");
                return null;
            }
            SimpleVectorStore store = newStore();
            store.load(indexFile.toFile());
            log.info("Loaded saved vector index from {}", indexFile);
            return store;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the saved vector index at {}; rebuilding it", indexFile, e);
            return null;
        }
    }

    private void save(SimpleVectorStore store, String fingerprint) {
        try {
            Files.createDirectories(directory);
            Path indexTemp = Files.createTempFile(directory, "index-", ".tmp");
            store.save(indexTemp.toFile());
            move(indexTemp, directory.resolve(INDEX_FILE));
            Path fingerprintTemp = Files.createTempFile(directory, "fingerprint-", ".tmp");
            Files.writeString(fingerprintTemp, fingerprint, StandardCharsets.UTF_8);
            move(fingerprintTemp, directory.resolve(FINGERPRINT_FILE));
            log.info("Saved vector index to {}", directory);
        } catch (IOException | RuntimeException e) {
            // The in-memory index still works; the next start will rebuild it.
            log.warn("Could not save the vector index to {}", directory, e);
        }
    }

    /** Replaces {@code target} atomically when the file system supports it, so readers never see a partial file. */
    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private SimpleVectorStore newStore() {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * Order-independent hash of the content that determines the embeddings. Metadata other than filename and title
     * (such as category or priority) is not included.
     */
    String fingerprint(List<Document> documents) {
        MessageDigest digest = sha256();
        update(digest, FORMAT_VERSION);
        update(digest, embeddingModelId);
        documents.stream()
                .map(document -> String.join("\u0000",
                        String.valueOf(document.getMetadata().get("filename")),
                        String.valueOf(document.getMetadata().get("title")),
                        Objects.requireNonNullElse(document.getText(), "")))
                .sorted()
                .forEach(entry -> update(digest, entry));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        // Length prefix keeps entries unambiguous.
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
