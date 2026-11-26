package com.hc.aiagenthyc.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the bundled interview question banks into one document per question, without embedding or indexing them.
 *
 * <p>Each file under {@code document/} follows this layout:</p>
 * <pre>
 * # 03 Java Backend
 *
 * ## Q03-001 Which JDK tools have you used?
 *
 * Category: Java Fundamentals | Priority: High
 *
 * Answer text, lists, tables and code blocks...
 * </pre>
 *
 * <p>A question and its whole answer, including code blocks, stay in one passage: every answer is short enough to
 * embed in one piece, and splitting would separate code or follow-up points from the question they answer. Headings
 * inside fenced code blocks are ignored. The metadata keeps the question ID, bank, category and priority so answers
 * can cite their source.</p>
 */
@Component
@Slf4j
public class KnowledgeBaseLoader {

    static final String LOCATION = "classpath*:document/*.md";

    static final String ID = "id";
    static final String TITLE = "title";
    static final String QUESTION = "question";
    static final String BANK = "bank";
    static final String CATEGORY = "category";
    static final String PRIORITY = "priority";
    static final String FILENAME = "filename";

    private static final Pattern QUESTION_HEADING = Pattern.compile("^##\\s+(Q\\d{2}-\\d{3})\\s+(.+?)\\s*$");
    private static final Pattern BANK_HEADING = Pattern.compile("^#\\s+(.+?)\\s*$");
    private static final Pattern META_LINE = Pattern.compile(
            "^Category:\\s*(.+?)\\s*\\|\\s*Priority:\\s*(\\w+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");

    private final ResourcePatternResolver resourcePatternResolver;

    public KnowledgeBaseLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * Parses every {@code document/*.md} file on the classpath. Does not call any model.
     *
     * @return one document per question, with the question ID as the document ID; empty (with a warning) when no file
     *         or question is found
     * @throws IllegalStateException when a resource cannot be read
     */
    public List<Document> loadMarkdown() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            // Resolve classpath resources so loading works from both an IDE and a packaged JAR.
            for (Resource resource : resourcePatternResolver.getResources(LOCATION)) {
                allDocuments.addAll(parse(resource.getFilename(), resource.getContentAsString(StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read knowledge-base Markdown files from " + LOCATION, e);
        }
        if (allDocuments.isEmpty()) {
            log.warn("No knowledge-base documents found at {}; RAG answers will have no reference material", LOCATION);
        } else {
            log.info("Loaded {} knowledge-base questions from {}", allDocuments.size(), LOCATION);
        }
        return allDocuments;
    }

    /**
     * Splits one question-bank file into one document per {@code ## Qnn-nnn} heading. Text before the first question
     * is dropped except the {@code #} bank heading; other {@code ##} headings stay in the current answer.
     */
    static List<Document> parse(String filename, String content) {
        List<Document> documents = new ArrayList<>();
        String bank = null;
        String id = null;
        String question = null;
        List<String> body = new ArrayList<>();
        boolean inFence = false;
        for (String line : content.replace("\r\n", "\n").split("\n", -1)) {
            // The fence line itself is kept in the answer body; only lines inside the fence skip heading detection.
            if (FENCE.matcher(line).find()) {
                inFence = !inFence;
            }
            if (!inFence) {
                Matcher heading = QUESTION_HEADING.matcher(line);
                if (heading.matches()) {
                    addDocument(documents, filename, bank, id, question, body);
                    id = heading.group(1);
                    question = heading.group(2);
                    body = new ArrayList<>();
                    continue;
                }
                Matcher bankHeading = BANK_HEADING.matcher(line);
                // A "#" line after the first question is ordinary answer text, not a new bank name.
                if (id == null && bankHeading.matches()) {
                    bank = bankHeading.group(1);
                    continue;
                }
            }
            if (id != null) {
                body.add(line);
            }
        }
        addDocument(documents, filename, bank, id, question, body);
        return documents;
    }

    private static void addDocument(List<Document> documents, String filename, String bank, String id,
                                    String question, List<String> body) {
        if (id == null) return;
        String category = null;
        String priority = null;
        int start = 0;
        // Only the first non-blank line may hold the "Category: ... | Priority: ..." metadata.
        while (start < body.size() && body.get(start).isBlank()) start++;
        if (start < body.size()) {
            Matcher meta = META_LINE.matcher(body.get(start).strip());
            if (meta.matches()) {
                category = meta.group(1);
                priority = meta.group(2);
                start++;
            }
        }
        String answer = String.join("\n", body.subList(start, body.size())).strip();
        if (answer.isEmpty()) {
            log.warn("Skipping {} in {}: the answer is empty", id, filename);
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ID, id);
        metadata.put(TITLE, id + " " + question);
        metadata.put(QUESTION, question);
        if (filename != null) metadata.put(FILENAME, filename);
        if (bank != null) metadata.put(BANK, bank);
        if (category != null) metadata.put(CATEGORY, category);
        if (priority != null) metadata.put(PRIORITY, priority);
        // The question is part of the embedded text, so a user question matches both the question and its answer.
        documents.add(new Document(id, "Question: " + question + "\n\nAnswer:\n" + answer, metadata));
    }
}
