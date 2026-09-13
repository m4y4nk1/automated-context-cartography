package com.vw.eacontext.dto;

/**
 * API-facing view of a declared {@link com.vw.eacontext.model.DataQualityGap}
 * from the source system's {@code KnownDataQualityGaps} sheet.
 */
public record DataQualityGapDto(
        String id,
        String gapType,
        String entityType,
        String entityId,
        String relatedApplicationId,
        String description,
        String severity) {
}
