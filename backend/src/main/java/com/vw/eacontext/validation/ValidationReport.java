package com.vw.eacontext.validation;

import java.util.List;

/**
 * The result of validating a canonical model: an immutable collection of
 * {@link ValidationIssue}s plus convenience accessors.
 *
 * @param issues all findings, in the order they were discovered
 */
public record ValidationReport(List<ValidationIssue> issues) {

    public ValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
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
}

