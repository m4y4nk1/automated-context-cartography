package com.vw.eacontext.dto;

import java.util.List;

import com.vw.eacontext.insight.Finding;

/**
 * Declared-vs-detected comparison for {@code GET /api/insights/gaps}: how many
 * gaps were pre-declared in {@code KnownDataQualityGaps} versus how much the
 * detector layer found overall, including issues never declared.
 *
 * @param declaredCount number of pre-declared {@code KnownDataQualityGaps} rows
 * @param detectedCount total number of findings produced by the detectors
 * @param newlyDetected findings whose related entities weren't already
 *                       covered by a declared gap
 */
public record GapComparisonDto(int declaredCount, int detectedCount, List<Finding> newlyDetected) {

    public GapComparisonDto {
        newlyDetected = newlyDetected == null ? List.of() : List.copyOf(newlyDetected);
    }
}
