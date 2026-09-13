package com.vw.eacontext.insight;

import java.util.List;

/**
 * Result of comparing detected {@link Finding}s against the pre-declared,
 * partial {@code KnownDataQualityGaps} sheet.
 *
 * @param declaredCount  number of pre-declared gaps in the dataset
 * @param detectedCount  total number of findings produced by the detectors
 * @param newlyDetected  findings whose related entities weren't already
 *                       covered by a declared gap — issues the detectors
 *                       surfaced that the pre-declared list didn't call out
 */
public record GapComparison(int declaredCount, int detectedCount, List<Finding> newlyDetected) {

    public GapComparison {
        newlyDetected = newlyDetected == null ? List.of() : List.copyOf(newlyDetected);
    }
}
