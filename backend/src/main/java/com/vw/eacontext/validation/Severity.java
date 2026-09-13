package com.vw.eacontext.validation;

/**
 * Severity of a {@link ValidationIssue}.
 */
public enum Severity {
    /** A blocking data-quality violation (e.g. missing required field, broken reference, duplicate id). */
    ERROR,
    /** A non-blocking data-quality gap worth attention (e.g. missing owner). */
    WARNING,
    /** Informational note. */
    INFO
}

