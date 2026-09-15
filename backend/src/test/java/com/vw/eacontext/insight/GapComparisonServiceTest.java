package com.vw.eacontext.insight;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.DataQualityGap;
import com.vw.eacontext.validation.Severity;

class GapComparisonServiceTest {

    private final GapComparisonService service = new GapComparisonService();

    @Test
    void aDeclaredGapCoversOnlyFindingsAboutItsOwnRecordNotEverythingTouchingItsRelatedApplication() {
        // Declared: REL-1 is a broken relationship, related to APP-A.
        CanonicalModel model = CanonicalModel.builder()
                .dataQualityGaps(List.of(DataQualityGap.builder()
                        .id("DQ-1").gapType("Broken Relationship").entityId("REL-1").relatedApplicationId("APP-A")
                        .build()))
                .build();
        Finding brokenRef = finding(FindingType.BROKEN_RELATIONSHIP_REFERENCE, "REL-1", "APP-A");
        Finding cycle = finding(FindingType.CIRCULAR_DEPENDENCY, "APP-A", "APP-B");

        GapComparison comparison = service.compare(model, List.of(brokenRef, cycle));

        assertThat(comparison.declaredCount()).isEqualTo(1);
        assertThat(comparison.detectedCount()).isEqualTo(2);
        // The cycle merely touches APP-A; nothing declared it, so it's newly detected.
        assertThat(comparison.newlyDetected()).containsExactly(cycle);
    }

    @Test
    void aGapNamingNoEntityFallsBackToItsRelatedApplication() {
        CanonicalModel model = CanonicalModel.builder()
                .dataQualityGaps(List.of(DataQualityGap.builder()
                        .id("DQ-1").gapType("Missing Owner").relatedApplicationId("APP-A").build()))
                .build();
        Finding missingOwner = finding(FindingType.OWNERSHIP_RECORD_MISSING, "APP-A");

        GapComparison comparison = service.compare(model, List.of(missingOwner));

        assertThat(comparison.newlyDetected()).isEmpty();
    }

    private static Finding finding(FindingType type, String... ids) {
        return new Finding(type, Severity.WARNING, List.of(ids), type.name());
    }
}
