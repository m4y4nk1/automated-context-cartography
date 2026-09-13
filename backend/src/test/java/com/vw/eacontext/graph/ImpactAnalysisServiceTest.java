package com.vw.eacontext.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.ImpactAnalysisResult;
import com.vw.eacontext.exception.EaNotFoundException;
import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.CanonicalModel;

@SpringBootTest
class ImpactAnalysisServiceTest {

    @Autowired
    private JsonEaDataParser parser;

    @Autowired
    private ImpactAnalysisService impactAnalysisService;

    private CanonicalModel model;

    @BeforeEach
    void loadSample() throws Exception {
        try (InputStream in = new ClassPathResource("sample_ea_dataset.json").getInputStream()) {
            model = parser.parse(in);
        }
    }

    @Test
    void computesUpstreamAndDownstreamBlastRadius() {
        ImpactAnalysisResult result = impactAnalysisService.impactAnalysis(model, "APP-BILL");

        // Downstream: BILL -> {OMS, ERP -> MDM}; MDM's own edges lead back to
        // already-visited nodes (OMS, BILL) so they add nothing further.
        assertThat(result.downstream()).containsExactlyInAnyOrder("APP-OMS", "APP-ERP", "APP-MDM");

        // Upstream: MDM -> BILL, MDM fed by ERP and DUPLICATE-B; ERP is also fed
        // by BILL itself, which is already visited as the origin.
        assertThat(result.upstream()).containsExactlyInAnyOrder("APP-MDM", "APP-ERP", "APP-DUPLICATE-B");

        assertThat(result.affected()).containsExactlyInAnyOrder(
                "APP-BILL", "APP-OMS", "APP-ERP", "APP-MDM", "APP-DUPLICATE-B");

        assertThat(result.edges()).extracting(GraphEdge::id)
                .containsExactlyInAnyOrder("REL-005", "REL-008", "REL-009", "REL-004", "REL-010", "REL-013");
    }

    @Test
    void hubHasNoDownstream() {
        // APP-OMS is a pure hub: everything depends on it, but it depends on nothing.
        ImpactAnalysisResult result = impactAnalysisService.impactAnalysis(model, "APP-OMS");

        assertThat(result.downstream()).isEmpty();
        assertThat(result.upstream()).isNotEmpty();
        assertThat(result.affected()).contains("APP-OMS");
    }

    @Test
    void unknownApplicationThrows() {
        assertThatThrownBy(() -> impactAnalysisService.impactAnalysis(model, "APP-NOPE"))
                .isInstanceOf(EaNotFoundException.class)
                .hasMessageContaining("APP-NOPE");
    }

    @Test
    void edgesReferenceOnlyAffectedNodes() {
        ImpactAnalysisResult result = impactAnalysisService.impactAnalysis(model, "APP-BILL");
        List<GraphEdge> edges = result.edges();
        assertThat(edges).allSatisfy(e -> {
            assertThat(result.affected()).contains(e.source());
            assertThat(result.affected()).contains(e.target());
        });
    }
}
