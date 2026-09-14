package com.vw.eacontext.insight;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.LifecycleStatus;
import com.vw.eacontext.validation.Severity;

@SpringBootTest
class InsightServiceTest {

    @Autowired
    private JsonEaDataParser parser;

    @Autowired
    private InsightService insightService;

    private List<Finding> findings;

    @BeforeEach
    void analyzeSample() throws Exception {
        CanonicalModel model;
        try (InputStream in = new ClassPathResource("sample_ea_dataset.json").getInputStream()) {
            model = parser.parse(in);
        }
        findings = insightService.analyze(model);
    }

    @Test
    void everyDetectorFiresAtLeastOnceOnTheSeededSample() {
        for (FindingType type : FindingType.values()) {
            assertThat(of(type)).as("expected at least one %s finding", type).isNotEmpty();
        }
    }

    @Test
    void hubDetectorFindsOmsByInDegree() {
        assertThat(relatedIds(FindingType.HUB)).containsExactly("APP-OMS");
        assertThat(of(FindingType.HUB)).allMatch(f -> f.severity() == Severity.ERROR);
    }

    @Test
    void circularDependencyDetectorFindsBillErpMdmCycle() {
        List<Finding> cycles = of(FindingType.CIRCULAR_DEPENDENCY);
        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0).relatedEntityIds())
                .containsExactlyInAnyOrder("APP-BILL", "APP-ERP", "APP-MDM");
    }

    @Test
    void brokenReferenceDetectorsFindTheGhostId() {
        assertThat(relatedIds(FindingType.BROKEN_RELATIONSHIP_REFERENCE)).contains("REL-011");
        assertThat(relatedIds(FindingType.DANGLING_INTERFACE_CONSUMER)).contains("IF-003");
        assertThat(relatedIds(FindingType.BROKEN_INFORMATION_FLOW_REFERENCE)).contains("FLOW-005");
        assertThat(relatedIds(FindingType.UNMAPPED_PROCESS_APPLICATION)).contains("BPM-005");
    }

    @Test
    void duplicateApplicationDetectorFindsTheAnalyticsSuitePair() {
        List<Finding> duplicates = of(FindingType.DUPLICATE_APPLICATION);
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.get(0).relatedEntityIds())
                .containsExactlyInAnyOrder("APP-DUPLICATE-A", "APP-DUPLICATE-B");
    }

    @Test
    void orphanApplicationDetectorFindsTheUnreferencedApp() {
        assertThat(relatedIds(FindingType.ORPHAN_APPLICATION)).containsExactly("APP-ORPHAN");
    }

    @Test
    void ownershipDetectorsMatchSample() {
        assertThat(relatedIds(FindingType.OWNERSHIP_RECORD_MISSING))
                .containsExactlyInAnyOrder("APP-ERP", "APP-DUPLICATE-B", "APP-INCONSISTENT");
        assertThat(relatedIds(FindingType.OWNERSHIP_PARTIAL_GAP)).contains("APP-BILL");
        assertThat(relatedIds(FindingType.MISSING_OWNER_FIELD)).containsExactly("APP-ERP");
    }

    @Test
    void lifecycleDetectorsMatchSample() {
        assertThat(relatedIds(FindingType.LIFECYCLE_RISK_CRITICAL_PROCESS)).contains("APP-LEGACY");
        assertThat(relatedIds(FindingType.LIFECYCLE_RISK_EOL_PROVIDER)).contains("APP-LEGACY");
        assertThat(relatedIds(FindingType.LIFECYCLE_INCONSISTENCY)).containsExactly("APP-INCONSISTENT");
        assertThat(relatedIds(FindingType.PHASE_OUT_CRITICAL_PATH)).contains("APP-PORTAL");
    }

    @Test
    void deprecatedInterfaceAndSensitiveFlowDetectorsMatchSample() {
        assertThat(relatedIds(FindingType.DEPRECATED_INTERFACE_IN_USE)).contains("FLOW-004");
        assertThat(relatedIds(FindingType.SENSITIVE_DATA_INSECURE_FLOW))
                .contains("FLOW-003", "FLOW-004");
    }

    @Test
    void interfaceWithoutRelationshipDetectorMatchesSample() {
        assertThat(relatedIds(FindingType.INTERFACE_WITHOUT_RELATIONSHIP))
                .contains("IF-001", "IF-003", "IF-004");
        // IF-002 and IF-005 each have a matching relationship (REL-005, REL-009) between the
        // same provider/consumer pair, so must not be flagged. IF-006's provider/consumer pair
        // (APP-PRICING/APP-PORTAL) also has a relationship connecting them (REL-012), just
        // recorded in the other direction (APP-PORTAL -> APP-PRICING) — Relationships and
        // Interfaces are captured independently in the source data and aren't guaranteed to
        // agree on which side is the dependent one, so a reverse-direction relationship still
        // counts as declared and IF-006 must not be flagged either.
        assertThat(relatedIds(FindingType.INTERFACE_WITHOUT_RELATIONSHIP))
                .doesNotContain("IF-002", "IF-005", "IF-006");
    }

    @Test
    void blankIdApplicationNeverCrashesADetectorInsteadOfBeingSkipped() {
        // A blank/missing Application id is something ValidationService
        // reports as an ERROR rather than rejecting outright (see
        // ValidationServiceTest) — so a model like this one genuinely reaches
        // InsightService in practice. Several detectors build each Finding's
        // relatedEntityIds from the application's own id; this application is
        // crafted to satisfy MissingOwnerFieldDetector (blank ownerEmployeeId)
        // and LifecycleInconsistencyDetector (Active, end date in the past)
        // simultaneously, since a null id previously reached List.of(app.id())
        // in both and threw NullPointerException instead of the app.id()-less
        // row simply being left out of that detector's findings.
        Application blankId = Application.builder()
                .id(null)
                .name("No Id At All")
                .lifecycleStatus(LifecycleStatus.ACTIVE)
                .lifecycleEndDate(LocalDate.now().minusDays(1))
                .build();
        CanonicalModel model = CanonicalModel.builder().applications(List.of(blankId)).build();

        List<Finding> result = insightService.analyze(model);

        assertThat(result.stream().flatMap(f -> f.relatedEntityIds().stream())).doesNotContainNull();
    }

    private List<Finding> of(FindingType type) {
        return findings.stream().filter(f -> f.type() == type).toList();
    }

    private List<String> relatedIds(FindingType type) {
        return of(type).stream().flatMap(f -> f.relatedEntityIds().stream()).toList();
    }
}
