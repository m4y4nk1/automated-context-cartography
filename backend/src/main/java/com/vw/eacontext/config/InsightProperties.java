package com.vw.eacontext.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the insight detectors.
 *
 * <p>Bound from {@code ea.insight.*} in {@code application.yml}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ea.insight")
public class InsightProperties {

    /**
     * An application whose in-degree in the {@code Relationships} graph
     * (number of other applications that depend on it) is strictly greater
     * than this threshold is flagged as a hub / potential single point of
     * failure.
     */
    private int hotspotDegreeThreshold = 5;

    /**
     * Whether {@code DuplicateApplicationDetector} treats application names as
     * case-sensitive when grouping. Defaults to {@code false} (case-insensitive
     * grouping catches more accidental duplicates).
     */
    private boolean duplicateNameCaseSensitive = false;

    /**
     * Optional lookahead window, in days, for lifecycle-risk detectors that
     * want to flag an upcoming retirement rather than only a past one. Zero
     * (the default) disables lookahead — only strictly past end dates count.
     */
    private int lifecycleRiskLookaheadDays = 0;
}
