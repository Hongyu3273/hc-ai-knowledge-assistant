package com.hc.aiagenthyc.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Measures retrieval quality against a labelled question set, without calling a chat model.
 *
 * <p>For answerable questions it reports hit rate at k (an expected entry is among the top k passages) and mean
 * reciprocal rank. For every question it records the best similarity score, so the scores of answerable and
 * out-of-scope questions can be compared when choosing a similarity threshold.</p>
 */
public final class RagEvaluator {

    private static final Pattern ENTRY_ID = Pattern.compile("^(Q\\d{2}-\\d{3})\\b");

    private final VectorStore store;
    private final int topK;

    /**
     * @param store vector store to search; each question costs one query embedding call
     * @param topK  number of passages retrieved per question
     */
    public RagEvaluator(VectorStore store, int topK) {
        this.store = store;
        this.topK = topK;
    }

    /**
     * Runs one similarity search per case and collects ranks, scores and timings. Exceptions from the store, such as
     * an embedding failure, propagate and abort the whole evaluation.
     */
    public RagEvalReport evaluate(List<RagEvalCase> cases) {
        List<RagEvalReport.CaseResult> results = new ArrayList<>();
        for (RagEvalCase evalCase : cases) {
            long start = System.nanoTime();
            List<Document> documents = store.similaritySearch(SearchRequest.builder()
                    .query(evalCase.question())
                    .topK(topK)
                    // Accept every score so the report shows raw scores for choosing a threshold.
                    .similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
                    .build());
            long millis = (System.nanoTime() - start) / 1_000_000;
            List<String> retrieved = new ArrayList<>();
            Double topScore = null;
            Double firstCorrectScore = null;
            int firstCorrectRank = 0;
            for (int i = 0; i < documents.size(); i++) {
                Document document = documents.get(i);
                String entryId = entryId(document);
                retrieved.add(entryId);
                Double score = RagSource.score(document);
                if (i == 0) topScore = score;
                if (firstCorrectRank == 0 && entryId != null && evalCase.expected().contains(entryId)) {
                    firstCorrectRank = i + 1;
                    firstCorrectScore = score;
                }
            }
            results.add(new RagEvalReport.CaseResult(evalCase, retrieved, firstCorrectRank, topScore,
                    firstCorrectScore, millis));
        }
        return new RagEvalReport(topK, results);
    }

    /**
     * Reads the question ID (such as {@code Q03-017}) from the {@code id} metadata, then from the title, then from the
     * start of the passage.
     */
    static String entryId(Document document) {
        Object id = document.getMetadata().get(KnowledgeBaseLoader.ID);
        if (id != null && ENTRY_ID.matcher(id.toString().strip().toUpperCase(Locale.ROOT)).find()) {
            return id.toString().strip().toUpperCase(Locale.ROOT);
        }
        Object title = document.getMetadata().get("title");
        String candidate = title != null ? title.toString().strip() : "";
        if (candidate.isEmpty() && document.getText() != null) {
            candidate = document.getText().strip().replaceFirst("^#+\\s*", "");
        }
        var matcher = ENTRY_ID.matcher(candidate.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : null;
    }
}
