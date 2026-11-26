package com.hc.aiagenthyc.rag;

import java.util.List;

/**
 * One retrieval evaluation question.
 *
 * @param id          case identifier used in the report
 * @param question    question as a user might ask it
 * @param expected    question IDs (for example {@code Q03-017}) that would answer it; empty when the knowledge base
 *                    has no answer
 */
public record RagEvalCase(String id, String question, List<String> expected) {
    public RagEvalCase {
        expected = expected == null ? List.of() : List.copyOf(expected);
    }

    /** Whether the knowledge base is expected to answer this question; out-of-scope cases are used for thresholds. */
    public boolean answerable() {
        return !expected.isEmpty();
    }
}
