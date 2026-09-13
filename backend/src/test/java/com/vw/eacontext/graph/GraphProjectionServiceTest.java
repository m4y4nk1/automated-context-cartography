package com.vw.eacontext.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.CanonicalModel;

@SpringBootTest
class GraphProjectionServiceTest {

    @Autowired
    private JsonEaDataParser parser;

    @Autowired
    private GraphProjectionService projectionService;

    private CanonicalModel model;

    @BeforeEach
    void loadSample() throws Exception {
        try (InputStream in = new ClassPathResource("sample_ea_dataset.json").getInputStream()) {
            model = parser.parse(in);
        }
    }

    @Test
    void applicationViewProjectsAppsAndRelationships() {
        GraphDto dto = projectionService.applicationView(model);

        assertThat(dto.frame()).isEqualTo("application");
        assertThat(dto.nodes()).hasSize(12);
        assertThat(dto.nodes()).allMatch(n -> "application".equals(n.type()));
        // 13 relationships minus REL-011 (ghost target) = 12 edges.
        assertThat(dto.edges()).hasSize(12);

        GraphEdge order = edge(dto, "REL-001");
        assertThat(order.source()).isEqualTo("APP-CRM");
        assertThat(order.target()).isEqualTo("APP-OMS");
        assertThat(order.type()).isEqualTo("DEPENDS_ON");
    }

    @Test
    void applicationNodesCarryDetailAndOwnershipFields() {
        GraphDto dto = projectionService.applicationView(model);

        // APP-CRM has an ownership record (OWN-001) in the sample fixture.
        GraphNode crm = node(dto, "APP-CRM");
        assertThat(crm.data().get("businessCriticality")).isEqualTo("MISSION_CRITICAL");
        assertThat(crm.data().get("hosting")).isEqualTo("SAAS");
        assertThat(crm.data().get("vendorType")).isEqualTo("COTS");
        assertThat(crm.data().get("costCenter")).isEqualTo("CC-100");
        assertThat(crm.data().get("description")).isNotNull();
        assertThat(crm.data().get("hasOwnershipRecord")).isEqualTo(true);
        assertThat(crm.data().get("applicationOwner")).isEqualTo("Alice Smith");
        assertThat(crm.data().get("businessOwner")).isEqualTo("VP Sales");

        // APP-ERP has no row in ApplicationOwnership at all in the sample fixture.
        GraphNode erp = node(dto, "APP-ERP");
        assertThat(erp.data().get("hasOwnershipRecord")).isEqualTo(false);
        assertThat(erp.data().get("applicationOwner")).isNull();
    }

    @Test
    void businessProcessViewLinksProcessesToSupportingApps() {
        GraphDto dto = projectionService.businessProcessView(model);

        assertThat(dto.frame()).isEqualTo("process");
        // 3 processes (BP-01/02/03) + 10 distinct supporting apps (BPM-005's
        // ghost supporting app is excluded) = 13 nodes.
        assertThat(dto.nodes().stream().filter(n -> "process".equals(n.type()))).hasSize(3);
        assertThat(dto.nodes().stream().filter(n -> "application".equals(n.type()))).hasSize(10);
        // 11 process mappings minus BPM-005 (ghost supporting app) = 10 edges.
        assertThat(dto.edges()).hasSize(10);
        assertThat(dto.edges()).anyMatch(e -> "BP-01".equals(e.source()) && "APP-OMS".equals(e.target()));
    }

    @Test
    void domainViewAggregatesAppsAndCollapsesEdges() {
        GraphDto dto = projectionService.domainView(model);

        assertThat(dto.frame()).isEqualTo("domain");
        // 5 distinct business domains across the 12 applications.
        assertThat(dto.nodes()).hasSize(5);
        assertThat(dto.nodes()).allMatch(n -> "domain".equals(n.type()));
        assertThat(node(dto, "Sales & Ordering").data().get("applicationCount")).isEqualTo(3);

        // Finance (Billing/Legacy) -> Sales & Ordering (OMS) collapses to one weighted edge.
        assertThat(dto.edges())
                .anyMatch(e -> "Finance".equals(e.source()) && "Sales & Ordering".equals(e.target()));
        assertThat(dto.edges()).allMatch(e -> e.data().containsKey("relationshipCount"));
    }

    @Test
    void informationFlowViewInsertsInformationObjects() {
        GraphDto dto = projectionService.informationFlowView(model);

        assertThat(dto.frame()).isEqualTo("informationObject");
        // FLOW-005 targets the ghost APP-9001 and is skipped; the remaining 4
        // flows each produce 2 edges (produces + consumes) = 8.
        assertThat(dto.edges()).hasSize(8);
        assertThat(dto.edges()).allMatch(e -> "produces".equals(e.type()) || "consumes".equals(e.type()));

        // 4 distinct information object names across the 4 valid flows.
        assertThat(dto.nodes().stream().filter(n -> "informationObject".equals(n.type()))).hasSize(4);
        assertThat(dto.nodes()).anyMatch(n -> "informationObject".equals(n.type())
                && "Customer Data".equals(n.label()));

        assertThat(dto.edges())
                .anyMatch(e -> "APP-MDM".equals(e.source()) && "produces".equals(e.type()));
    }

    private static GraphNode node(GraphDto dto, String id) {
        return dto.nodes().stream().filter(n -> id.equals(n.id())).findFirst().orElseThrow();
    }

    private static GraphEdge edge(GraphDto dto, String id) {
        return dto.edges().stream().filter(e -> id.equals(e.id())).findFirst().orElseThrow();
    }
}
