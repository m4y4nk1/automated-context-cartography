package com.vw.eacontext.model;

/**
 * Data-sensitivity classification of an {@link InformationObject}.
 * {@link #CONFIDENTIAL_PII} and {@link #RESTRICTED_PCI} (and, conservatively,
 * {@link #CONFIDENTIAL}) must be treated as sensitive throughout the system —
 * in DTOs, logs, and exports.
 */
public enum Classification {
    INTERNAL,
    CONFIDENTIAL,
    CONFIDENTIAL_PII,
    RESTRICTED_PCI;

    /** @return {@code true} for classifications that must be handled as sensitive data. */
    public boolean isSensitive() {
        return this != INTERNAL;
    }
}
