package com.vw.eacontext.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.List;

import org.jgrapht.Graph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.dto.SimulatedRemovalResult;
import com.vw.eacontext.exception.EaNotFoundException;
import com.vw.eacontext.graph.GraphBuilderService;
import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

/**
 * Ground truth for the seeded sample (see InsightServiceTest / GraphBuilderServiceTest):
 * APP-OMS is the hub, with in-degree 7 — APP-CRM, APP-PRICING, APP-PORTAL, APP-MDM, APP-BILL,
 * APP-LEGACY and APP-DUPLICATE-A all have a relationship depending on it.
 */
@SpringBootTest
class ChangeSimulationServiceTest {

    @Autowired
    private JsonEaDataParser parser;

    @Autowired
    private GraphBuilderService graphBuilderService;

    @Autowired
    private InsightService insightService;

    @Autowired
    private ChangeSimulationService changeSimulationService;

    private CanonicalModel model;
    private Graph<Application, RelationshipEdge> graph;
    private List<Finding> baselineFindings;

    @BeforeEach
    void loadSample() throws Exception {
        try (InputStream in = new ClassPathResource("sample_ea_dataset.json").getInputStream()) {
            model = parser.parse(in);
        }
        graph = graphBuilderService.build(model);
        baselineFindings = insightService.analyze(model, graph);
    }

    @Test
    void removingTheHubResolvesItsHubFindingAndBreaksItsDependents() {
        SimulatedRemovalResult result = changeSimulationService.simulateRemoval(model, graph, baselineFindings, "APP-OMS");

        assertThat(result.removedApplicationId()).isEqualTo("APP-OMS");
        // downstream() — everything depending on OMS — is the full transitive closure
        // (ImpactAnalysisService does a BFS, not just direct neighbors), so it's a superset of
        // the 7 direct in-degree contributors — assert that known subset, not an exact match.
        assertThat(result.downstream()).contains(
                "APP-CRM", "APP-PRICING", "APP-PORTAL", "APP-MDM", "APP-BILL", "APP-LEGACY", "APP-DUPLICATE-A");

        // The HUB finding about OMS itself disappears — the app is gone, so it can't be a hub anymore.
        assertThat(result.resolvedFindings())
                .anyMatch(f -> f.type() == FindingType.HUB && f.relatedEntityIds().contains("APP-OMS"));

        // Every relationship that used to target OMS now targets a ghost id — each of OMS's 7
        // dependents gets a brand-new broken-relationship-reference finding it didn't have before.
        List<Finding> newBrokenRefs = result.newFindings().stream()
                .filter(f -> f.type() == FindingType.BROKEN_RELATIONSHIP_REFERENCE)
                .toList();
        assertThat(newBrokenRefs).isNotEmpty();
        assertThat(newBrokenRefs).anyMatch(f -> f.relatedEntityIds().contains("APP-CRM"));

        // Findings unrelated to OMS at all (e.g. the BILL/ERP/MDM cycle) must be unaffected —
        // neither newly appearing nor resolved.
        assertThat(result.newFindings()).noneMatch(f -> f.type() == FindingType.CIRCULAR_DEPENDENCY);
        assertThat(result.resolvedFindings()).noneMatch(f -> f.type() == FindingType.CIRCULAR_DEPENDENCY);
    }

    @Test
    void unknownApplicationIdThrows() {
        assertThatThrownBy(() -> changeSimulationService.simulateRemoval(model, graph, baselineFindings, "APP-NOPE"))
                .isInstanceOf(EaNotFoundException.class);
    }
}
