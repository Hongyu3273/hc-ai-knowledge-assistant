package com.hc.aiagenthyc.rag;

import java.util.List;

/**
 * A model answer together with the knowledge-base passages that were supplied to the model.
 *
 * @param content answer text from the model
 * @param sources passages in retrieval order; empty when nothing was retrieved
 */
public record RagAnswer(String content, List<RagSource> sources) {
    public RagAnswer {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
