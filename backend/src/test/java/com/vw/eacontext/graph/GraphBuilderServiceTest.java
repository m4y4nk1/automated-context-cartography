package com.vw.eacontext.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.jgrapht.Graph;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.RelationshipType;

@SpringBootTest
class GraphBuilderServiceTest {

    @Autowired
    private JsonEaDataParser parser;

    @Autowired
    private GraphBuilderService graphBuilderService;

    @Test
    void buildsDirectedGraphFromSample() throws Exception {
        CanonicalModel model;
        try (InputStream in = new ClassPathResource("sample_ea_dataset.json").getInputStream()) {
            model = parser.parse(in);
        }

        Graph<Application, RelationshipEdge> graph = graphBuilderService.build(model);

        // 12 applications -> 12 vertices (the ghost APP-9001 is never added).
        assertThat(graph.vertexSet()).hasSize(12);
        // 13 relationships, but REL-011 targets the ghost APP-9001 so its edge is skipped -> 12.
        assertThat(graph.edgeSet()).hasSize(12);

        Application crm = vertex(graph, "APP-CRM");
        Application oms = vertex(graph, "APP-OMS");

        // Edge direction is source -> target (REL-001: CRM -> OMS).
        RelationshipEdge orderEdge = graph.getEdge(crm, oms);
        assertThat(orderEdge).isNotNull();
        assertThat(orderEdge.getRelationshipId()).isEqualTo("REL-001");
        assertThat(orderEdge.getRelationshipType()).isEqualTo(RelationshipType.DEPENDS_ON);
        assertThat(graph.getEdgeSource(orderEdge)).isEqualTo(crm);
        assertThat(graph.getEdgeTarget(orderEdge)).isEqualTo(oms);

        // No reverse edge OMS -> CRM: OMS is never a relationship source.
        assertThat(graph.getEdge(oms, crm)).isNull();

        // OMS is the seeded hub: in-degree 7 (CRM, PRICING, PORTAL, MDM, BILL, LEGACY, DUPLICATE-A).
        assertThat(graph.inDegreeOf(oms)).isEqualTo(7);
    }

    private static Application vertex(Graph<Application, RelationshipEdge> graph, String id) {
        return graph.vertexSet().stream()
                .filter(a -> id.equals(a.id()))
                .findFirst()
                .orElseThrow();
    }
}
