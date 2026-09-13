package com.vw.eacontext.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.dto.GraphStats;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.validation.Severity;

class TemplateSummaryGeneratorTest {

    private final SummaryGenerator generator = new TemplateSummaryGenerator();

    private static final GraphStats STATS = GraphStats.builder()
            .applicationCount(12)
            .relationshipCount(12)
            .interfaceCount(6)
            .domainCount(5)
            .businessProcessCount(3)
            .informationObjectCount(5)
            .mostConnectedApplicationId("APP-OMS")
            .maxDegree(7)
            .hubCount(1)
            .cycleCount(1)
            .orphanCount(1)
            .build();

    private static final List<Finding> FINDINGS = List.of(
            new Finding(FindingType.HUB, Severity.ERROR, List.of("APP-OMS"), "x"),
            new Finding(FindingType.OWNERSHIP_RECORD_MISSING, Severity.WARNING, List.of("APP-ERP"), "x"),
            new Finding(FindingType.OWNERSHIP_RECORD_MISSING, Severity.WARNING, List.of("APP-DUPLICATE-B"), "x"),
            new Finding(FindingType.LIFECYCLE_INCONSISTENCY, Severity.WARNING, List.of("APP-INCONSISTENT"), "x"),
            new Finding(FindingType.ORPHAN_APPLICATION, Severity.WARNING, List.of("APP-ORPHAN"), "x"));

    @Test
    void producesDeterministicOutput() {
        String first = generator.summarize(FINDINGS, STATS);
        String second = generator.summarize(FINDINGS, STATS);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void includesOverviewTotalsAndSections() {
        String summary = generator.summarize(FINDINGS, STATS);

        assertThat(summary)
                .contains("Analyzed 12 application(s) across 5 domain(s) and 3 business process(es)")
                .contains("connected by 12 relationship(s) and 6 interface(s); 5 information flow(s) tracked.")
                .contains("Detected 5 issue(s): 1 error(s), 4 warning(s), 0 informational.")
                .contains("Hubs (single points of failure): 1 finding(s) touching application(s) (APP-OMS).")
                .contains("Ownership gaps: 2 finding(s) touching record(s) (APP-DUPLICATE-B, APP-ERP).")
                .contains("Lifecycle risks: 1 finding(s) touching record(s) (APP-INCONSISTENT).")
                .contains("Orphan applications: 1 finding(s) touching application(s) (APP-ORPHAN).")
                .contains("Most connected application: APP-OMS (in-degree 7).")
                .contains("1 hub application(s) exceed the dependency threshold.")
                .contains("1 circular dependency chain(s) detected.")
                .contains("1 application(s) are orphaned");
    }

    @Test
    void ownershipIdsAreSortedForDeterminism() {
        List<Finding> unordered = List.of(
                new Finding(FindingType.OWNERSHIP_RECORD_MISSING, Severity.WARNING, List.of("APP-DUPLICATE-B"), "x"),
                new Finding(FindingType.OWNERSHIP_RECORD_MISSING, Severity.WARNING, List.of("APP-ERP"), "x"));
        String summary = generator.summarize(unordered, STATS);
        assertThat(summary).contains("(APP-DUPLICATE-B, APP-ERP)");
    }

    @Test
    void handlesNoFindings() {
        String summary = generator.summarize(List.of(), STATS);
        assertThat(summary).contains("No issues were detected.");
    }
}
