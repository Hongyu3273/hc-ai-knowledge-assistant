package com.hc.aiagenthyc.rag;

import org.springframework.ai.document.Document;

import java.util.Map;

/**
 * A knowledge-base passage used to answer a question.
 *
 * @param id       question ID such as {@code Q03-017}, when known
 * @param bank     question bank name, when known
 * @param category category within the bank, when known
 * @param filename source file name, when known
 * @param title    question ID and question text, when known
 * @param score    similarity score from 0 to 1, when the retriever reports one
 * @param excerpt  beginning of the passage text
 */
public record RagSource(String id, String bank, String category, String filename, String title, Double score,
                        String excerpt) {

    /**
     * Builds a source from a retrieved document. Works for both local passages, which carry the loader's metadata, and
     * cloud documents, which may not; missing fields become {@code null}.
     *
     * @param document     retrieved document
     * @param excerptChars maximum excerpt length in characters before an ellipsis is appended
     */
    public static RagSource from(Document document, int excerptChars) {
        Map<String, Object> metadata = document.getMetadata();
        String text = document.getText() == null ? "" : document.getText().strip();
        String title = stringValue(metadata.get("title"));
        // Cloud documents have no title metadata; fall back to the first line without Markdown heading marks.
        if (title == null && !text.isEmpty()) {
            title = text.lines().findFirst().orElse("").replaceFirst("^#+\\s*", "").strip();
        }
        return new RagSource(stringValue(metadata.get("id")), stringValue(metadata.get("bank")),
                stringValue(metadata.get("category")), stringValue(metadata.get("filename")), title, score(document),
                excerpt(answerPart(text), excerptChars));
    }

    /**
     * Returns the document's similarity score, or derives it from a {@code distance} metadata value as
     * {@code 1 - distance} for retrievers that report distance instead; {@code null} when neither is present.
     */
    static Double score(Document document) {
        if (document.getScore() != null) return document.getScore();
        Object distance = document.getMetadata().get("distance");
        if (distance instanceof Number number) return 1.0 - number.doubleValue();
        return null;
    }

    /** Knowledge-base passages start with "Question: ... Answer:"; the excerpt shows the answer. */
    private static String answerPart(String text) {
        int answer = text.indexOf("\nAnswer:\n");
        return text.startsWith("Question:") && answer >= 0 ? text.substring(answer + "\nAnswer:\n".length()).strip() : text;
    }

    private static String excerpt(String text, int maxChars) {
        String singleLine = text.replaceAll("\\s+", " ");
        if (singleLine.length() <= maxChars) return singleLine;
        return singleLine.substring(0, Math.max(0, maxChars)).strip() + "…";
    }

    private static String stringValue(Object value) {
        if (value == null) return null;
        String text = value.toString().strip();
        return text.isEmpty() ? null : text;
    }
}
