package com.hc.aiagenthyc.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Unit test of {@link KnowledgeBaseLoader}: parsing the question-bank Markdown layout into one document per question.
 * The parsing tests use inline samples; one test also checks the user's own question banks when they are present.
 */
class KnowledgeBaseLoaderTest {

    // The code block contains a "#" comment and a "##" line that must not be read as bank or question headings.
    private static final String SAMPLE = """
            # 03 Java Backend

            ## Q03-001 Which JDK tools have you used?

            Category: Java Fundamentals | Priority: High

            I use jcmd and jstack most.

            ```bash
            # list Java processes
            jcmd
            ## not a new question
            ```

            Then MAT for heap dumps.

            ## Q03-002 What is a Selector?

            Category: Java Fundamentals | Priority: Medium

            A Selector lets one thread watch many channels.
            """;

    @Test
    void eachQuestionBecomesOnePassageWithItsCodeAndMetadata() {
        List<Document> documents = KnowledgeBaseLoader.parse("03-java-backend.md", SAMPLE);

        assertThat(documents).hasSize(2);
        Document first = documents.get(0);
        assertThat(first.getId()).isEqualTo("Q03-001");
        assertThat(first.getText())
                .startsWith("Question: Which JDK tools have you used?")
                .contains("jcmd and jstack", "# list Java processes", "## not a new question", "Then MAT")
                // The category line becomes metadata and is removed from the embedded text.
                .doesNotContain("Category:");
        assertThat(first.getMetadata())
                .containsEntry("id", "Q03-001")
                .containsEntry("title", "Q03-001 Which JDK tools have you used?")
                .containsEntry("question", "Which JDK tools have you used?")
                .containsEntry("bank", "03 Java Backend")
                .containsEntry("category", "Java Fundamentals")
                .containsEntry("priority", "High")
                .containsEntry("filename", "03-java-backend.md");
        assertThat(documents.get(1).getMetadata()).containsEntry("priority", "Medium");
    }

    /** Q01-001 has only a category line, so its answer is empty; Q01-002 has an answer but no category line. */
    @Test
    void questionsWithoutAnAnswerAreSkipped() {
        String content = "# 01 Bank\n\n## Q01-001 Empty?\n\nCategory: General | Priority: Low\n\n## Q01-002 Kept?\n\nYes.\n";

        List<Document> documents = KnowledgeBaseLoader.parse("01.md", content);

        assertThat(documents).extracting(Document::getId).containsExactly("Q01-002");
        assertThat(documents.get(0).getMetadata()).doesNotContainKey("category");
    }

    /**
     * Checks the question banks in src/main/resources/document/. They are not in Git (each user supplies their own),
     * so the test is skipped when none are present.
     */
    @Test
    void loadsEveryLocalQuestionWithAUniqueId() {
        List<Document> documents = new KnowledgeBaseLoader(new PathMatchingResourcePatternResolver()).loadMarkdown();
        assumeFalse(documents.isEmpty(), "No question banks in src/main/resources/document/; add your own to run this test");

        assertThat(documents).extracting(Document::getId).doesNotHaveDuplicates();
        assertThat(documents).allSatisfy(document -> assertThat(document.getMetadata())
                .containsKeys("filename", "bank", "category", "priority"));
        // The banks must not link to images from the question-bank app, which are not part of this project.
        // The loader does not strip such links, so this checks the source files themselves.
        assertThat(documents).noneSatisfy(document -> assertThat(document.getText()).contains("](images/"));
    }
}
