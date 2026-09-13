package com.vw.eacontext.insight.detector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.config.InsightProperties;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

class DuplicateApplicationDetectorTest {

    private final DuplicateApplicationDetector detector = new DuplicateApplicationDetector(new InsightProperties());

    /**
     * Default config is case-insensitive with whitespace collapsed, so "CRM Suite",
     * "crm suite" and "CRM  Suite " (mixed case, doubled/trailing spaces) must still
     * be recognized as the same name and grouped into one finding — not treated as
     * three unrelated applications (a false negative).
     */
    @Test
    void groupsNamesDespiteCaseAndWhitespaceVariance() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(
                        Application.builder().id("APP-1").name("CRM Suite").build(),
                        Application.builder().id("APP-2").name("crm suite").build(),
                        Application.builder().id("APP-3").name("CRM  Suite ").build(),
                        Application.builder().id("APP-4").name("Billing Engine").build()))
                .build();

        List<Finding> findings = detector.detect(model, null);

        assertThat(findings).hasSize(1);
        Finding finding = findings.get(0);
        assertThat(finding.type()).isEqualTo(FindingType.DUPLICATE_APPLICATION);
        assertThat(finding.relatedEntityIds()).containsExactlyInAnyOrder("APP-1", "APP-2", "APP-3");
    }

    @Test
    void doesNotFlagDistinctNames() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(
                        Application.builder().id("APP-1").name("CRM Suite").build(),
                        Application.builder().id("APP-2").name("Billing Engine").build()))
                .build();

        assertThat(detector.detect(model, null)).isEmpty();
    }
}
