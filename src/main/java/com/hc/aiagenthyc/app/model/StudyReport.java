package com.hc.aiagenthyc.app.model;

import java.util.List;

/**
 * Structured study report: the main gaps to work on and what to revise next.
 *
 * <p>Parsed from the model's JSON answer; {@code KnowledgeAssistant} rejects a report with a blank title or with no
 * non-blank suggestions.</p>
 *
 * @param title report title
 * @param suggestions suggested actions, ordered by priority
 */
public record StudyReport(String title, List<String> suggestions) {
}
