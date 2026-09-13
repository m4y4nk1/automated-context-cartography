package com.vw.eacontext.validation;

/**
 * A single validation finding.
 *
 * @param severity the {@link Severity} of the finding
 * @param message  a human-readable description of the issue
 */
public record ValidationIssue(Severity severity, String message) {

    public static ValidationIssue error(String message) {
        return new ValidationIssue(Severity.ERROR, message);
    }

    public static ValidationIssue warning(String message) {
        return new ValidationIssue(Severity.WARNING, message);
    }

    public static ValidationIssue info(String message) {
        return new ValidationIssue(Severity.INFO, message);
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + message;
    }
}

