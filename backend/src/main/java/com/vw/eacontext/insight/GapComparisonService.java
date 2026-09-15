package com.vw.eacontext.insight;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.DataQualityGap;

/**
 * Compares detected {@link Finding}s against the pre-declared, partial
 * {@code KnownDataQualityGaps} sheet, so the service layer can report how many
 * declared gaps exist alongside how many issues the detectors found overall —
 * the participant guide explicitly rewards surfacing gaps beyond the
 * pre-declared list.
 */
@Service
public class GapComparisonService {

    public GapComparison compare(CanonicalModel model, List<Finding> findings) {
        Set<String> declaredEntityIds = new HashSet<>();
        for (DataQualityGap gap : model.dataQualityGaps()) {
            // Matched on the specific offending record. RelatedApplicationID is
            // only context: a broken relationship declared on REL-0065 relates to
            // APP-0005, but that doesn't make every other finding touching
            // APP-0005 — an undeclared dependency cycle, say — "declared" too.
            // It's only used when a gap names no entity at all.
            if (gap.entityId() != null && !gap.entityId().isBlank()) {
                declaredEntityIds.add(gap.entityId());
            } else {
                addIfPresent(declaredEntityIds, gap.relatedApplicationId());
            }
        }

        List<Finding> newlyDetected = findings.stream()
                .filter(finding -> finding.relatedEntityIds().stream().noneMatch(declaredEntityIds::contains))
                .toList();

        return new GapComparison(model.dataQualityGaps().size(), findings.size(), newlyDetected);
    }

    private void addIfPresent(Set<String> ids, String id) {
        if (id != null && !id.isBlank()) {
            ids.add(id);
        }
    }
}
