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
            addIfPresent(declaredEntityIds, gap.entityId());
            addIfPresent(declaredEntityIds, gap.relatedApplicationId());
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
