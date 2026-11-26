package com.hc.aiagenthyc.rag;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Retrieval evaluation results with summary metrics and a Markdown rendering.
 *
 * @param topK  number of passages retrieved per question
 * @param cases one result per evaluated question, in input order
 */
public record RagEvalReport(int topK, List<CaseResult> cases) {

    /**
     * Retrieval outcome for one evaluation question.
     *
     * @param evalCase          the question that was evaluated
     * @param retrievedEntryIds question IDs of the retrieved passages in rank order; an element is {@code null} when no
     *                          ID could be read from the passage
     * @param firstCorrectRank  1-based rank of the first expected entry, or 0 when none was retrieved
     * @param topScore          similarity score of the top-ranked passage, or {@code null} when nothing was retrieved
     *                          or the store reports no score
     * @param firstCorrectScore similarity score of the passage at {@code firstCorrectRank}, or {@code null} on a miss
     * @param millis            wall-clock time of the similarity search, including the query embedding call
     */
    public record CaseResult(RagEvalCase evalCase, List<String> retrievedEntryIds, int firstCorrectRank,
                             Double topScore, Double firstCorrectScore, long millis) {
        public boolean hit() {
            return firstCorrectRank > 0;
        }
    }

    public RagEvalReport {
        cases = List.copyOf(cases);
    }

    /** Number of cases that have at least one expected entry; the denominator of hit rate and MRR. */
    public long answerableCount() {
        return cases.stream().filter(c -> c.evalCase().answerable()).count();
    }

    /** Share of answerable questions whose expected entry is in the top k. */
    public double hitRate() {
        long answerable = answerableCount();
        if (answerable == 0) return 0;
        return (double) cases.stream().filter(c -> c.evalCase().answerable() && c.hit()).count() / answerable;
    }

    /** Mean reciprocal rank of the first expected entry; a miss counts as 0. */
    public double meanReciprocalRank() {
        long answerable = answerableCount();
        if (answerable == 0) return 0;
        return cases.stream().filter(c -> c.evalCase().answerable())
                .mapToDouble(c -> c.hit() ? 1.0 / c.firstCorrectRank() : 0).sum() / answerable;
    }

    /** Average retrieval time over all cases, answerable or not; empty when there are no cases. */
    public OptionalDouble averageMillis() {
        return cases.stream().mapToLong(CaseResult::millis).average();
    }

    /** Lowest score of a correct passage among hits. */
    public OptionalDouble minCorrectScore() {
        return cases.stream().filter(c -> c.evalCase().answerable() && c.hit())
                .map(CaseResult::firstCorrectScore).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).min();
    }

    /** Highest top-1 score among questions the knowledge base cannot answer. */
    public OptionalDouble maxOutOfScopeScore() {
        return cases.stream().filter(c -> !c.evalCase().answerable())
                .map(CaseResult::topScore).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).max();
    }

    /**
     * A threshold midway between the best out-of-scope score and the worst correct score, when the two groups
     * separate cleanly. Empty when they overlap or either group is missing.
     */
    public OptionalDouble suggestedThreshold() {
        OptionalDouble correct = minCorrectScore();
        OptionalDouble outOfScope = maxOutOfScopeScore();
        if (correct.isEmpty() || outOfScope.isEmpty() || outOfScope.getAsDouble() >= correct.getAsDouble()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((correct.getAsDouble() + outOfScope.getAsDouble()) / 2);
    }

    /** Renders a summary metrics table followed by a per-case table, for saving as a Markdown report. */
    public String toMarkdown() {
        StringBuilder out = new StringBuilder();
        out.append("# Retrieval evaluation\n\n");
        out.append("| Metric | Value |\n| --- | --- |\n");
        out.append("| Questions | ").append(cases.size()).append(" (")
                .append(answerableCount()).append(" answerable, ")
                .append(cases.size() - answerableCount()).append(" out of scope) |\n");
        out.append("| Hit rate @").append(topK).append(" | ").append(percent(hitRate())).append(" |\n");
        out.append("| Mean reciprocal rank | ").append(format(meanReciprocalRank())).append(" |\n");
        out.append("| Lowest correct-passage score | ").append(format(minCorrectScore())).append(" |\n");
        out.append("| Highest out-of-scope top score | ").append(format(maxOutOfScopeScore())).append(" |\n");
        out.append("| Suggested similarity threshold | ")
                .append(suggestedThreshold().isPresent() ? format(suggestedThreshold())
                        : "none (scores overlap or a group is missing)").append(" |\n");
        out.append("| Average retrieval time | ").append(averageMillis().isPresent()
                ? Math.round(averageMillis().getAsDouble()) + " ms" : "n/a").append(" |\n\n");

        out.append("## Cases\n\n| ID | Expected | Retrieved (rank order) | First correct rank | Top score | Question |\n");
        out.append("| --- | --- | --- | --- | --- | --- |\n");
        for (CaseResult result : cases) {
            RagEvalCase evalCase = result.evalCase();
            out.append("| ").append(evalCase.id())
                    .append(" | ").append(evalCase.answerable() ? String.join(", ", evalCase.expected()) : "(none)")
                    .append(" | ").append(String.join(", ", result.retrievedEntryIds().stream()
                            .map(id -> id == null ? "?" : id).toList()))
                    .append(" | ").append(evalCase.answerable() ? (result.hit() ? result.firstCorrectRank() : "miss") : "-")
                    .append(" | ").append(format(result.topScore()))
                    .append(" | ").append(evalCase.question().replace("|", "\\|"))
                    .append(" |\n");
        }
        return out.toString();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String format(Double value) {
        return value == null ? "n/a" : format(value.doubleValue());
    }

    private static String format(OptionalDouble value) {
        return value.isPresent() ? format(value.getAsDouble()) : "n/a";
    }
}
