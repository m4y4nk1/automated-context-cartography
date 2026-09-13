package com.vw.eacontext.insight;

import java.util.List;

import com.vw.eacontext.validation.Severity;

/**
 * A single insight produced by a {@link Detector}.
 *
 * @param type             the {@link FindingType category} of finding
 * @param severity         the {@link Severity} of the finding
 * @param relatedEntityIds every entity id this finding traces back to (never
 *                         empty for a well-formed finding) — a cycle's member
 *                         application ids, a duplicate-name group's app ids, a
 *                         broken reference's owning record id, etc.
 * @param message          a human-readable description
 */
public record Finding(FindingType type, Severity severity, List<String> relatedEntityIds, String message) {

    public Finding {
        relatedEntityIds = relatedEntityIds == null ? List.of() : List.copyOf(relatedEntityIds);
    }
}
