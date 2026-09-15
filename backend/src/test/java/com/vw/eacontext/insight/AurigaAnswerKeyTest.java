package com.vw.eacontext.insight;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;

import org.jgrapht.Graph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.graph.GraphBuilderService;
import com.vw.eacontext.graph.GraphProjectionService;
import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.ingestion.ExcelEaDataParser;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

/**
 * Every scenario in the Auriga workbook's own {@code AnswerKey} sheet (S01–S18),
 * checked end to end against the real, unmodified workbook: parse, build the
 * graph, run every detector. Anchor ids below come from the AnswerKey — they are
 * the expected answers, not something production code is keyed on.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AurigaAnswerKeyTest {

    @Autowired
    private ExcelEaDataParser parser;

    @Autowired
    private GraphBuilderService graphBuilderService;

    @Autowired
    private InsightService insightService;

    @Autowired
    private GapComparisonService gapComparisonService;

    @Autowired
    private GraphProjectionService projectionService;

    private CanonicalModel model;
    private List<Finding> findings;

    @BeforeAll
    void analyzeWorkbook() throws Exception {
        try (InputStream in = new ClassPathResource("Auriga_Motors_Synthetic_EA_Dataset.xlsx").getInputStream()) {
            model = parser.parse(in);
        }
        Graph<Application, RelationshipEdge> graph = graphBuilderService.build(model);
        findings = insightService.analyze(model, graph);
    }

    @Test
    void s01HubsIamAndApiGateway() {
        assertThat(ids(FindingType.HUB)).contains("APP-0033", "APP-0034");
    }

    @Test
    void s02CircularDependencyOmsPricingCrm() {
        assertThat(of(FindingType.CIRCULAR_DEPENDENCY))
                .anyMatch(f -> f.relatedEntityIds().containsAll(List.of("APP-0005", "APP-0006", "APP-0001"))
                        && f.relatedEntityIds().size() == 3);
    }

    @Test
    void s03ToS06BrokenReferencesAcrossEverySheet() {
        assertThat(ids(FindingType.BROKEN_RELATIONSHIP_REFERENCE)).contains("REL-0065");
        assertThat(ids(FindingType.DANGLING_INTERFACE_CONSUMER)).contains("IF-0036");
        assertThat(ids(FindingType.BROKEN_INFORMATION_FLOW_REFERENCE)).contains("FLOW-0030");
        assertThat(ids(FindingType.UNMAPPED_PROCESS_APPLICATION)).contains("BPM-0038", "BP-12");
    }

    @Test
    void s07DuplicateDealerPortal() {
        assertThat(of(FindingType.DUPLICATE_APPLICATION))
                .anyMatch(f -> f.relatedEntityIds().containsAll(List.of("APP-0002", "APP-0046")));
    }

    @Test
    void s08OrphanApplications() {
        assertThat(ids(FindingType.ORPHAN_APPLICATION)).containsExactlyInAnyOrder("APP-0032", "APP-0046");
    }

    @Test
    void s09ToS11OwnershipGaps() {
        assertThat(ids(FindingType.OWNERSHIP_RECORD_MISSING))
                .containsExactlyInAnyOrder("APP-0039", "APP-0042", "APP-0046");
        assertThat(ids(FindingType.OWNERSHIP_PARTIAL_GAP)).contains("OWN-0035", "APP-0035");
        assertThat(ids(FindingType.MISSING_OWNER_FIELD))
                .containsExactlyInAnyOrder("APP-0039", "APP-0042", "APP-0046");
    }

    @Test
    void s12ToS14LifecycleRisks() {
        assertThat(ids(FindingType.LIFECYCLE_RISK_CRITICAL_PROCESS)).contains("APP-0027", "BP-09");
        assertThat(ids(FindingType.LIFECYCLE_RISK_EOL_PROVIDER)).contains("IF-0020", "APP-0027");
        assertThat(ids(FindingType.LIFECYCLE_INCONSISTENCY)).contains("APP-0020");
    }

    @Test
    void s15ToS17InterfaceAndFlowRisks() {
        assertThat(ids(FindingType.DEPRECATED_INTERFACE_IN_USE)).contains("FLOW-0029", "IF-0037");
        assertThat(ids(FindingType.INTERFACE_WITHOUT_RELATIONSHIP)).contains("IF-0038");
        assertThat(ids(FindingType.SENSITIVE_DATA_INSECURE_FLOW)).contains("FLOW-0029");
    }

    @Test
    void s18PhaseOutAppOnVehicleOrdering() {
        assertThat(ids(FindingType.PHASE_OUT_CRITICAL_PATH)).contains("APP-0040", "BP-01");
    }

    @Test
    void theSeededCycleIsNotMistakenForADeclaredGap() {
        // DQ-003 declares REL-0065 (related to APP-0005). The OMS -> Pricing -> CRM
        // cycle also touches APP-0005 but was never declared anywhere.
        GapComparison comparison = gapComparisonService.compare(model, findings);

        assertThat(comparison.declaredCount()).isEqualTo(7);
        assertThat(comparison.newlyDetected()).anyMatch(f -> f.type() == FindingType.CIRCULAR_DEPENDENCY);
        assertThat(comparison.newlyDetected()).noneMatch(f -> f.type() == FindingType.BROKEN_RELATIONSHIP_REFERENCE);
    }

    @Test
    void domainFrameKeepsEveryGhostEndpointAndAllTwelveDomains() {
        GraphDto dto = projectionService.domainView(model);

        assertThat(dto.nodes().stream().filter(n -> "domain".equals(n.type()))).hasSize(12);
        // REL-0065 -> APP-9001, IF-0036 -> APP-9002 and FLOW-0030 -> APP-9004 each
        // leave a real domain on the other end; APP-9003 is only in a process mapping.
        assertThat(dto.nodes().stream().filter(n -> "applicationGhost".equals(n.type())).map(n -> n.id()))
                .containsExactlyInAnyOrder("APP-9001", "APP-9002", "APP-9004");
    }

    private List<Finding> of(FindingType type) {
        return findings.stream().filter(f -> f.type() == type).toList();
    }

    private List<String> ids(FindingType type) {
        return of(type).stream().flatMap(f -> f.relatedEntityIds().stream()).toList();
    }
}
