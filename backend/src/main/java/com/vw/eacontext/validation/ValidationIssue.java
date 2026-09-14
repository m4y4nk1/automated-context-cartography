package com.vw.eacontext.validation;

/**
 * A single validation finding.
 *
 * <p>{@code sheet}, {@code recordId} and {@code field} are structured context —
 * present wherever the check that raised the issue had it available — so a
 * caller can group/filter issues (by sheet, say) without parsing
 * {@code message} text. All three are {@code null} when not applicable (e.g.
 * an ingestion-layer diagnostic that isn't about one specific record).
 * {@code message} is always populated and always human-readable on its own,
 * so nothing is lost for a caller that only wants a flat, readable list.
 *
 * @param severity the {@link Severity} of the finding
 * @param message  a human-readable description of the issue, specific enough
 *                 to act on without needing the structured fields
 * @param sheet    the entity type / sheet the issue concerns (e.g.
 *                 {@code "Application"}), or {@code null}
 * @param recordId the offending record's own id, or {@code null} when the
 *                 issue isn't about one specific record (e.g. a row with no
 *                 id at all, or a file-level/structural diagnostic)
 * @param field    the column/field name the issue concerns, or {@code null}
 */
public record ValidationIssue(Severity severity, String message, String sheet, String recordId, String field) {

    public static ValidationIssue error(String message) {
        return new ValidationIssue(Severity.ERROR, message, null, null, null);
    }

    public static ValidationIssue warning(String message) {
        return new ValidationIssue(Severity.WARNING, message, null, null, null);
    }

    public static ValidationIssue info(String message) {
        return new ValidationIssue(Severity.INFO, message, null, null, null);
    }

    /** With structured context: which sheet/entity, which record, which field (any may be {@code null}). */
    public static ValidationIssue error(String sheet, String recordId, String field, String message) {
        return new ValidationIssue(Severity.ERROR, message, sheet, recordId, field);
    }

    public static ValidationIssue warning(String sheet, String recordId, String field, String message) {
        return new ValidationIssue(Severity.WARNING, message, sheet, recordId, field);
    }

    public static ValidationIssue info(String sheet, String recordId, String field, String message) {
        return new ValidationIssue(Severity.INFO, message, sheet, recordId, field);
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + message;
    }
}
