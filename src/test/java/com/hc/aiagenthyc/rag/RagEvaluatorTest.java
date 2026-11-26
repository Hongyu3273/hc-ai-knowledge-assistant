package com.hc.aiagenthyc.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test of {@link RagEvaluator} and the metrics in {@link RagEvalReport}. A Mockito vector store returns fixed
 * passages per question, so the expected hit rate, reciprocal rank and threshold can be worked out by hand.
 */
class RagEvaluatorTest {

    private final VectorStore store = mock(VectorStore.class);

    private static Document passage(String title, double score) {
        return Document.builder().text("text").metadata(Map.of("title", title)).score(score).build();
    }

    /** Stubs the store to return these passages, in this rank order, for searches with exactly this query. */
    private void answer(String question, Document... documents) {
        when(store.similaritySearch(argThat((SearchRequest r) -> r != null && question.equals(r.getQuery()))))
                .thenReturn(List.of(documents));
    }

    @Test
    void computesHitRateReciprocalRankAndSuggestedThreshold() {
        answer("q1", passage("Q03-001 JDK tools", 0.80), passage("Q03-002 Selectors", 0.70));
        answer("q2", passage("Q06-010 Redo log", 0.75), passage("Q06-011 Undo log", 0.72));
        answer("q3", passage("Q05-001 Hash vs history mode", 0.60), passage("Q05-002 Mounted hook", 0.55));
        answer("q4", passage("Q11-003 Why this role", 0.30));

        RagEvalReport report = new RagEvaluator(store, 2).evaluate(List.of(
                new RagEvalCase("c1", "q1", List.of("Q03-001")),          // rank 1
                new RagEvalCase("c2", "q2", List.of("Q06-011")),          // rank 2
                new RagEvalCase("c3", "q3", List.of("Q08-020")),          // miss
                new RagEvalCase("c4", "q4", List.of())));             // out of scope

        // c4 expects nothing, so it counts only toward the out-of-scope score, not the hit rate or MRR.
        assertThat(report.answerableCount()).isEqualTo(3);
        assertThat(report.hitRate()).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(report.meanReciprocalRank()).isCloseTo((1.0 + 0.5 + 0) / 3, within(1e-9));
        // Lowest score among correct passages: 0.80 for c1 and 0.72 for c2; the miss c3 contributes no score.
        assertThat(report.minCorrectScore().getAsDouble()).isEqualTo(0.72);
        assertThat(report.maxOutOfScopeScore().getAsDouble()).isEqualTo(0.30);
        // Midway between the best out-of-scope score and the worst correct score: (0.30 + 0.72) / 2.
        assertThat(report.suggestedThreshold().getAsDouble()).isCloseTo(0.51, within(1e-9));
        assertThat(report.toMarkdown()).contains("| c2 | Q06-011 | Q06-010, Q06-011 | 2 |", "| c3 | Q08-020 | Q05-001, Q05-002 | miss |");
    }

    @Test
    void noThresholdIsSuggestedWhenScoresOverlap() {
        answer("q1", passage("Q03-001 JDK tools", 0.50));
        answer("q2", passage("Q11-003 Why this role", 0.60));

        RagEvalReport report = new RagEvaluator(store, 4).evaluate(List.of(
                new RagEvalCase("c1", "q1", List.of("Q03-001")),
                new RagEvalCase("c2", "q2", List.of())));

        // The out-of-scope question scores 0.60, above the correct passage's 0.50, so no threshold separates them.
        assertThat(report.suggestedThreshold()).isEmpty();
    }

    @Test
    void readsEntryIdsFromMetadataTitlesOrHeadings() {
        assertThat(RagEvaluator.entryId(Document.builder().text("text").metadata(Map.of("id", "Q07-012")).build()))
                .isEqualTo("Q07-012");
        assertThat(RagEvaluator.entryId(passage("Q04-031 What is the GIL?", 0.5))).isEqualTo("Q04-031");
        // Without metadata the ID is read from the text, after any Markdown heading marks, ignoring case.
        assertThat(RagEvaluator.entryId(new Document("## q02-005 What is RAG?"))).isEqualTo("Q02-005");
        assertThat(RagEvaluator.entryId(new Document("No identifier here"))).isNull();
    }
}
