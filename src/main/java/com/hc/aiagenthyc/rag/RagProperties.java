package com.hc.aiagenthyc.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Retrieval settings for the interview question knowledge base. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.assistant.rag")
public class RagProperties {

    /** Maximum number of passages sent to the model for one question (local retrieval only). */
    @Min(1) @Max(20) private int topK = 5;

    /**
     * Minimum similarity score (0 to 1) a passage needs to be used in local retrieval. 0 accepts every passage.
     * Tune it with the retrieval evaluation report before raising it.
     */
    @DecimalMin("0.0") @DecimalMax("1.0") private double similarityThreshold = 0.0;

    /** Maximum characters of each passage returned as a source excerpt. */
    @Min(0) @Max(2000) private int sourceExcerptChars = 200;

    @Valid private Index index = new Index();
    @Valid private Cloud cloud = new Cloud();

    /** Local vector index persistence settings ({@code app.assistant.rag.index.*}). */
    @Getter
    @Setter
    public static class Index {
        /** Save the embedded index to disk and reuse it while the documents and embedding model are unchanged. */
        private boolean persist = true;
        /** Directory for the saved index and fingerprint; a relative path resolves against the working directory. */
        @NotBlank private String directory = "./tmp/rag-index";
    }

    /** DashScope cloud retrieval settings ({@code app.assistant.rag.cloud.*}). */
    @Getter
    @Setter
    public static class Cloud {
        /** Creates the DashScope cloud retrieval Advisor and exposes POST /assistant/rag/cloud. */
        private boolean enabled = false;
        /** Name of an existing knowledge-base index in the DashScope console. */
        @NotBlank private String indexName = "Interview Knowledge Base";
    }
}
