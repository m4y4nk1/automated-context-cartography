package com.vw.eacontext.validation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of validating a canonical model: an immutable collection of
 * {@link ValidationIssue}s plus convenience accessors and a pre-computed
 * {@link Summary} so a caller (notably the frontend upload flow) can show a
 * meaningful overview without re-deriving counts from the flat list itself.
 *
 * @param issues  all findings, in the order they were discovered
 * @param summary counts by severity and by sheet; auto-derived from
 *                {@code issues} when not supplied explicitly
 */
public record ValidationReport(List<ValidationIssue> issues, Summary summary) {

    public ValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (summary == null) {
            summary = Summary.of(issues);
        }
    }

    /** Convenience constructor: derives {@link Summary} from {@code issues}. */
    public ValidationReport(List<ValidationIssue> issues) {
        this(issues, null);
    }

    /** Issues matching the given severity. */
    public List<ValidationIssue> issuesOf(Severity severity) {
        return issues.stream().filter(i -> i.severity() == severity).toList();
    }

    public List<ValidationIssue> errors() {
        return issuesOf(Severity.ERROR);
    }

    public List<ValidationIssue> warnings() {
        return issuesOf(Severity.WARNING);
    }

    /** {@code true} if at least one ERROR-severity issue was found. */
    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    /** {@code true} if there are no ERROR-severity issues (warnings are allowed). */
    public boolean isValid() {
        return !hasErrors();
    }

    /** Total number of issues. */
    public int count() {
        return issues.size();
    }

    /**
     * Pre-computed counts, grouped for a summary view: how many of each
     * severity, and how many per sheet/entity (an issue with no {@code sheet}
     * context is bucketed under {@code "General"}).
     *
     * @param errorCount   number of ERROR-severity issues
     * @param warningCount number of WARNING-severity issues
     * @param infoCount    number of INFO-severity issues
     * @param bySheet      issue count per {@link ValidationIssue#sheet()}
     *                     (or {@code "General"}), in first-seen order
     */
    public record Summary(int errorCount, int warningCount, int infoCount, Map<String, Integer> bySheet) {

        public Summary {
            bySheet = bySheet == null ? Map.of() : Map.copyOf(bySheet);
        }

        static Summary of(List<ValidationIssue> issues) {
            int errorCount = 0;
            int warningCount = 0;
            int infoCount = 0;
            Map<String, Integer> bySheet = new LinkedHashMap<>();
            for (ValidationIssue issue : issues) {
                switch (issue.severity()) {
                    case ERROR -> errorCount++;
                    case WARNING -> warningCount++;
                    case INFO -> infoCount++;
                }
                String sheet = issue.sheet() == null ? "General" : issue.sheet();
                bySheet.merge(sheet, 1, Integer::sum);
            }
            return new Summary(errorCount, warningCount, infoCount, bySheet);
        }
    }
}
