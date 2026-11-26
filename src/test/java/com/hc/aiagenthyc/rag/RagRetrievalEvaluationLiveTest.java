package com.hc.aiagenthyc.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Measures retrieval quality of the real knowledge base with the real embedding model (no chat model calls).
 * Run with RUN_LIVE_AI_TESTS=true. Writes target/rag-eval/report.md; use it to choose
 * {@code app.assistant.rag.similarity-threshold}.
 *
 * <p>Loads the full application with the default "local" profile, so it needs a DashScope key for the embedding
 * model. It also needs the user's own question banks in src/main/resources/document/ and a labelled question set in
 * src/test/resources/rag-eval/eval-set.json; neither is in Git, and the test is skipped without the question set.</p>
 */
@Tag("live-ai")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Timeout(value = 15, unit = TimeUnit.MINUTES) // the first run embeds all 1,233 questions; later runs reuse the index
class RagRetrievalEvaluationLiveTest {

    private static final Path OUTPUT = Path.of("target", "rag-eval").toAbsolutePath();

    /** Regression floor for hit rate; raise it once the baseline is known. */
    private static final double MIN_HIT_RATE = 0.7;

    @Autowired
    @Qualifier("knowledgeBaseVectorStore")
    private VectorStore store;

    @Autowired
    private RagProperties ragProperties;

    // Keep the evaluation index under target/ so it never replaces the application's own index.
    @DynamicPropertySource
    static void isolateIndex(DynamicPropertyRegistry registry) {
        registry.add("app.assistant.rag.index.directory", () -> OUTPUT.resolve("index").toString());
    }

    @Test
    void retrievalMeetsTheHitRateFloor() throws Exception {
        // The question set is labelled with IDs from your own question banks and is not in Git.
        ClassPathResource evalSet = new ClassPathResource("rag-eval/eval-set.json");
        assumeTrue(evalSet.exists(), "No src/test/resources/rag-eval/eval-set.json; write one for your question banks");
        List<RagEvalCase> cases;
        try (InputStream input = evalSet.getInputStream()) {
            cases = new ObjectMapper().readValue(input, new TypeReference<>() { });
        }

        // Evaluate at the same top-k the application uses. The evaluator ignores the similarity threshold.
        RagEvalReport report = new RagEvaluator(store, ragProperties.getTopK()).evaluate(cases);

        Files.createDirectories(OUTPUT);
        Path markdown = OUTPUT.resolve("report.md");
        Files.writeString(markdown, report.toMarkdown(), StandardCharsets.UTF_8);
        System.out.println("Retrieval evaluation report: " + markdown);
        System.out.println(report.toMarkdown());

        assertThat(report.hitRate())
                .as("hit rate @%d; see %s for the cases that missed", ragProperties.getTopK(), markdown)
                .isGreaterThanOrEqualTo(MIN_HIT_RATE);
    }
}
