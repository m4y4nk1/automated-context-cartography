package com.vw.eacontext.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * A pre-declared, partial data-quality gap from the source system's
 * {@code KnownDataQualityGaps} sheet. Kept separate from computed
 * {@link com.vw.eacontext.insight.Finding}s so the service layer can compare
 * "declared" vs. "detected" (see {@code GapComparisonService}).
 *
 * @param id                    unique gap identifier (required)
 * @param gapType               category of the declared issue (free text, e.g. "Missing Owner")
 * @param entityType            kind of entity the gap concerns (e.g. Application, Relationship)
 * @param entityId              id of the offending record
 * @param relatedApplicationId  application the gap relates to
 * @param description           human-readable explanation
 * @param severity              declared severity
 * @param attributes            unrecognized source columns, retained verbatim
 */
@Builder
public record DataQualityGap(
        @NotBlank String id,
        String gapType,
        String entityType,
        String entityId,
        String relatedApplicationId,
        String description,
        DependencyCriticality severity,
        Map<String, String> attributes
) {
    public DataQualityGap {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
