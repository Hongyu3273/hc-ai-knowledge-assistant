package com.hc.aiagenthyc.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test of {@link PersistentVectorIndex}: when a saved index is reused or rebuilt, and how documents are batched
 * for embedding. A counting fake embedding model replaces DashScope, so the number of embedded texts shows whether
 * the index was rebuilt. No network or API key is needed.
 */
class PersistentVectorIndexTest {

    @TempDir Path directory;
    private final CountingEmbeddingModel embeddings = new CountingEmbeddingModel();

    private static List<Document> documents(String secondText) {
        return List.of(
                new Document("Pause and resume the discussion later.", Map.of("filename", "a.md", "title", "L01 Pause")),
                new Document(secondText, Map.of("filename", "b.md", "title", "M02 Money")));
    }

    @Test
    void reusesASavedIndexWhileDocumentsAreUnchanged() {
        new PersistentVectorIndex(embeddings, "model-a", directory).loadOrBuild(documents("Plan a shared budget."));
        int afterFirstBuild = embeddings.embeddedTexts.get();
        assertThat(afterFirstBuild).isEqualTo(2);
        assertThat(directory.resolve(PersistentVectorIndex.INDEX_FILE)).exists();
        assertThat(directory.resolve(PersistentVectorIndex.FINGERPRINT_FILE)).exists();

        var reloaded = new PersistentVectorIndex(embeddings, "model-a", directory).loadOrBuild(documents("Plan a shared budget."));

        assertThat(embeddings.embeddedTexts.get()).as("no documents re-embedded").isEqualTo(afterFirstBuild);
        // The reloaded store must still answer queries from the saved embeddings.
        var results = reloaded.similaritySearch(SearchRequest.builder().query("Plan a shared budget.").topK(1).build());
        assertThat(results).singleElement().satisfies(d -> assertThat(d.getText()).isEqualTo("Plan a shared budget."));
    }

    @Test
    void rebuildsWhenADocumentChanges() {
        new PersistentVectorIndex(embeddings, "model-a", directory).loadOrBuild(documents("Plan a shared budget."));

        new PersistentVectorIndex(embeddings, "model-a", directory).loadOrBuild(documents("Review spending monthly."));

        // Any change rebuilds the whole index: two texts for the first build plus both texts again.
        assertThat(embeddings.embeddedTexts.get()).isEqualTo(4);
    }

    @Test
    void rebuildsWhenTheEmbeddingModelChanges() {
        new PersistentVectorIndex(embeddings, "model-a", directory).loadOrBuild(documents("Plan a shared budget."));

        new PersistentVectorIndex(embeddings, "model-b", directory).loadOrBuild(documents("Plan a shared budget."));

        assertThat(embeddings.embeddedTexts.get()).isEqualTo(4);
    }

    @Test
    void rebuildsWhenTheSavedIndexIsCorrupt() throws Exception {
        new PersistentVectorIndex(embeddings, "model-a", directory).loadOrBuild(documents("Plan a shared budget."));
        Files.writeString(directory.resolve(PersistentVectorIndex.INDEX_FILE), "{not json");

        var rebuilt = new PersistentVectorIndex(embeddings, "model-a", directory).loadOrBuild(documents("Plan a shared budget."));

        assertThat(embeddings.embeddedTexts.get()).isEqualTo(4);
        assertThat(rebuilt.similaritySearch(SearchRequest.builder().query("pause").topK(2).build())).hasSize(2);
    }

    @Test
    void documentOrderDoesNotChangeTheFingerprint() {
        // A null directory is enough here: fingerprint() does not read or write files.
        var index = new PersistentVectorIndex(embeddings, "model-a", null);
        List<Document> forward = documents("Plan a shared budget.");
        List<Document> reversed = new ArrayList<>(forward);
        java.util.Collections.reverse(reversed);

        assertThat(index.fingerprint(forward)).isEqualTo(index.fingerprint(reversed));
    }

    @Test
    void withoutADirectoryNothingIsSaved() {
        new PersistentVectorIndex(embeddings, "model-a", null).loadOrBuild(documents("Plan a shared budget."));

        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void largeKnowledgeBasesAreEmbeddedInSmallBatches() {
        List<Document> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            many.add(new Document("Question " + i + " about caching.", Map.of("filename", "a.md", "title", "Q01-0" + i)));
        }

        var store = new PersistentVectorIndex(embeddings, "model-a", null).loadOrBuild(many);

        assertThat(embeddings.embeddedTexts.get()).isEqualTo(25);
        assertThat(embeddings.largestRequest.get()).isLessThanOrEqualTo(PersistentVectorIndex.EMBED_BATCH_SIZE);
        // The default similarity threshold accepts every document, so any search returns results.
        assertThat(store.similaritySearch("caching")).isNotEmpty();
    }

    /** Deterministic letter-frequency embeddings that count how many texts were embedded. */
    static final class CountingEmbeddingModel implements EmbeddingModel {
        final AtomicInteger embeddedTexts = new AtomicInteger();
        final AtomicInteger largestRequest = new AtomicInteger();

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> results = new ArrayList<>();
            List<String> texts = request.getInstructions();
            largestRequest.accumulateAndGet(texts.size(), Math::max);
            for (int i = 0; i < texts.size(); i++) {
                results.add(new Embedding(vector(texts.get(i)), i));
            }
            return new EmbeddingResponse(results);
        }

        // Without this the default dimensions() embeds a probe text, which would be counted.
        @Override
        public int dimensions() {
            return 26;
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        /** Counts the call and returns one dimension per letter a to z. */
        float[] vector(String text) {
            embeddedTexts.incrementAndGet();
            float[] vector = new float[26];
            for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
                if (c >= 'a' && c <= 'z') vector[c - 'a']++;
            }
            return vector;
        }
    }
}
