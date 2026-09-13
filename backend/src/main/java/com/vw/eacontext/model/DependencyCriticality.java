package com.vw.eacontext.model;

/**
 * Criticality rating used for {@link Relationship#dependencyCriticality()} and,
 * reused for the same High/Medium/Low vocabulary, {@link DataQualityGap#severity()}.
 */
public enum DependencyCriticality {
    HIGH,
    MEDIUM,
    LOW
}
