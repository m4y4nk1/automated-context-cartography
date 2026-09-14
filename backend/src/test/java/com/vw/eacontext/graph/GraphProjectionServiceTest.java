package com.vw.eacontext.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.model.RelationshipType;

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
        // 12 applications + a placeholder for the ghost APP-9001, which REL-011
        // and FLOW-005 both reference.
        assertThat(dto.nodes()).hasSize(13);
        assertThat(dto.nodes().stream().filter(n -> "application".equals(n.type()))).hasSize(12);
        assertThat(dto.nodes().stream().filter(n -> "applicationGhost".equals(n.type()))).hasSize(1);
        // 13 relationships + 6 interfaces + 5 flows = 24 records, each its own
        // edge now. Nothing is dropped, nothing is merged.
        assertThat(dto.edges()).hasSize(24);
    }

    @Test
    void everyRecordBetweenAPairBecomesItsOwnEdge() {
        GraphDto dto = projectionService.applicationView(model);

        // A pair with only a relationship: REL-001 is APP-CRM (dependent) ->
        // APP-OMS (provider) as recorded, but drawn provider -> dependent, so
        // the arrowhead lands on the dependent application (APP-CRM) — the
        // same convention every edge type uses. See ApplicationEdgeAssembler's
        // class javadoc.
        GraphEdge order = edge(dto, "REL-001");
        assertThat(order.source()).isEqualTo("APP-OMS");
        assertThat(order.target()).isEqualTo("APP-CRM");
        assertThat(order.type()).isEqualTo("DEPENDS_ON");
        assertThat(order.label()).isEqualTo("depends on");
        assertThat(order.data().get("memberIds")).isEqualTo(List.of("REL-001"));

        // A pair described three times over (REL-005, IF-002, FLOW-002) is now
        // three separate edges, each with its own specific label, rather than
        // one edge with a merged label.
        assertThat(edge(dto, "REL-005").label()).isEqualTo("depends on");
        assertThat(edge(dto, "IF-002").label()).isEqualTo("Billing Feed");
        assertThat(edge(dto, "FLOW-002").label()).isEqualTo("Invoice Data");
    }

    @Test
    void interfaceAndFlowEdgesCarryFullFieldCoverage() {
        GraphDto dto = projectionService.applicationView(model);

        // IF-002 in the sample fixture: Protocol SOAP, DataFormat XML, Frequency
        // "Daily batch" — protocol/interfaceStatus were already covered before
        // this pass; dataFormat/frequency were the two fields ApplicationEdgeAssembler
        // silently dropped.
        GraphEdge iface = edge(dto, "IF-002");
        assertThat(iface.data().get("dataFormats")).isEqualTo(List.of("XML"));
        assertThat(iface.data().get("frequencies")).isEqualTo(List.of("DAILY_BATCH"));

        // FLOW-002 in the sample fixture: Operation "Create".
        GraphEdge flow = edge(dto, "FLOW-002");
        assertThat(flow.data().get("operations")).isEqualTo(List.of("CREATE"));
    }

    @Test
    void ghostReferencesBecomePlaceholderNodesInsteadOfVanishing() {
        GraphDto dto = projectionService.applicationView(model);

        GraphNode ghost = node(dto, "APP-9001");
        assertThat(ghost.type()).isEqualTo("applicationGhost");
        assertThat(ghost.data().get("unresolved")).isEqualTo(true);
        // Every record pointing at the missing id, across all four record types.
        assertThat(ghost.data().get("referencedBy"))
                .isEqualTo(List.of("REL-011", "IF-003", "FLOW-005", "BPM-005"));

        // The dangling edge is now drawn rather than silently discarded.
        assertThat(dto.edges()).anyMatch(e -> "APP-9001".equals(e.target()) || "APP-9001".equals(e.source()));
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
    void applicationNodesExposeOwnershipRecordsOwnEmployeeIdSeparately() {
        // Application.ownerEmployeeId and ApplicationOwnership.ownerEmployeeId are
        // two distinct source fields meant to join to each other; a hand-built
        // model where they disagree confirms both remain independently visible
        // instead of one silently masking the other (see GraphProjectionService's
        // applicationNode()).
        CanonicalModel handBuilt = CanonicalModel.builder()
                .applications(List.of(Application.builder()
                        .id("APP-A").name("A").ownerEmployeeId("E-FROM-APP").build()))
                .applicationOwnerships(List.of(ApplicationOwnership.builder()
                        .id("OWN-1").applicationId("APP-A").ownerEmployeeId("E-FROM-OWNERSHIP").build()))
                .build();

        GraphDto dto = projectionService.applicationView(handBuilt);

        GraphNode app = node(dto, "APP-A");
        assertThat(app.data().get("ownerEmployeeId")).isEqualTo("E-FROM-APP");
        assertThat(app.data().get("ownershipEmployeeId")).isEqualTo("E-FROM-OWNERSHIP");
    }

    @Test
    void applicationNodesCarryDeclaredDataQualityGaps() {
        GraphDto dto = projectionService.applicationView(model);

        // DQ-002 in the sample fixture relates to APP-CRM.
        GraphNode crm = node(dto, "APP-CRM");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) crm.data().get("declaredGaps");
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0)).containsEntry("gapType", "Broken Relationship")
                .containsEntry("severity", "HIGH");
        assertThat(gaps.get(0).get("description")).asString().contains("APP-9001");

        // APP-OMS has no declared gap in the sample fixture.
        GraphNode oms = node(dto, "APP-OMS");
        assertThat((List<?>) oms.data().get("declaredGaps")).isEmpty();
    }

    @Test
    void businessProcessViewLinksProcessesToSupportingApps() {
        GraphDto dto = projectionService.businessProcessView(model);

        assertThat(dto.frame()).isEqualTo("process");
        // 3 processes (BP-01/02/03) + 10 real supporting apps + a placeholder
        // for BPM-005's ghost supporting app = 14 nodes.
        assertThat(dto.nodes().stream().filter(n -> "process".equals(n.type()))).hasSize(3);
        assertThat(dto.nodes().stream().filter(n -> "application".equals(n.type()))).hasSize(10);
        assertThat(dto.nodes().stream().filter(n -> "applicationGhost".equals(n.type()))).hasSize(1);
        // All 11 mappings are now drawn; BPM-005 lands on the placeholder rather
        // than being dropped, so a process mapped only to missing applications
        // can no longer disappear from the frame entirely.
        assertThat(dto.edges()).hasSize(11);
        assertThat(dto.edges()).anyMatch(e -> "BP-01".equals(e.source()) && "APP-OMS".equals(e.target()));
        assertThat(dto.edges()).anyMatch(e -> "BPM-005".equals(e.id()) && "APP-9001".equals(e.target()));
    }

    /**
     * Dormant on every shipped dataset (every businessProcessId resolves), but
     * a modified dataset could seed one — the application side of that mapping
     * should still show up rather than vanishing along with the broken process.
     */
    @Test
    void businessProcessViewMaterializesAPlaceholderForAnUnresolvedProcessReference() {
        CanonicalModel handBuilt = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").name("A").build()))
                .processMappings(List.of(ProcessMapping.builder()
                        .id("BPM-1").businessProcessId("BP-999").supportingApplicationId("APP-A").build()))
                .build();

        GraphDto dto = projectionService.businessProcessView(handBuilt);

        assertThat(dto.nodes()).anyMatch(n -> "BP-999".equals(n.id()) && "processGhost".equals(n.type()));
        assertThat(dto.nodes()).anyMatch(n -> "APP-A".equals(n.id()) && "application".equals(n.type()));
        assertThat(dto.edges()).anyMatch(e -> "BPM-1".equals(e.id())
                && "BP-999".equals(e.source()) && "APP-A".equals(e.target()));
    }

    @Test
    void domainViewAggregatesAppsAndCollapsesEdges() {
        GraphDto dto = projectionService.domainView(model);

        assertThat(dto.frame()).isEqualTo("domain");
        // 5 distinct business domains across the 12 applications, plus a
        // placeholder for the ghost APP-9001 that REL-011 (APP-CRM -> APP-9001)
        // references — domain counts themselves stay unaffected by the ghost.
        assertThat(dto.nodes().stream().filter(n -> "domain".equals(n.type()))).hasSize(5);
        assertThat(dto.nodes().stream().filter(n -> "applicationGhost".equals(n.type()))).hasSize(1);
        assertThat(node(dto, "Sales & Ordering").data().get("applicationCount")).isEqualTo(3);

        // Finance (Billing/Legacy) -> Sales & Ordering (OMS) collapses to one weighted edge.
        assertThat(dto.edges())
                .anyMatch(e -> "Finance".equals(e.source()) && "Sales & Ordering".equals(e.target()));
        assertThat(dto.edges()).allMatch(e -> e.data().containsKey("relationshipCount"));

        // Sales & Ordering (APP-CRM) -> the ghost, instead of the edge silently vanishing.
        assertThat(dto.edges())
                .anyMatch(e -> "Sales & Ordering".equals(e.source()) && "APP-9001".equals(e.target()));
    }

    /**
     * The sample fixture's own ghost is target-side only, so this exercises the
     * other direction with a hand-built model: a relationship whose SOURCE is
     * unresolved. Dormant on every shipped dataset, but a modified one could
     * seed it either way.
     */
    @Test
    void domainViewMaterializesAGhostOnTheSourceSideOfARelationshipToo() {
        CanonicalModel handBuilt = CanonicalModel.builder()
                .applications(List.of(
                        Application.builder().id("APP-A").name("A").businessDomain("Sales").build()))
                .relationships(List.of(Relationship.builder()
                        .id("REL-1").sourceApplicationId("APP-9099").targetApplicationId("APP-A")
                        .relationshipType(RelationshipType.DEPENDS_ON).build()))
                .build();

        GraphDto dto = projectionService.domainView(handBuilt);

        assertThat(dto.nodes()).anyMatch(n -> "APP-9099".equals(n.id()) && "applicationGhost".equals(n.type()));
        // Ghost (source) -> the target application's domain, not silently dropped.
        assertThat(dto.edges()).anyMatch(e -> "APP-9099".equals(e.source()) && "Sales".equals(e.target()));
    }

    @Test
    void informationFlowViewInsertsInformationObjects() {
        GraphDto dto = projectionService.informationFlowView(model);

        assertThat(dto.frame()).isEqualTo("informationObject");
        // All 5 flows are drawn, each as 2 edges (produces + consumes) = 10.
        // FLOW-005 targets the ghost APP-9001, which now gets a placeholder
        // node instead of taking the whole flow down with it.
        assertThat(dto.edges()).hasSize(10);
        assertThat(dto.edges()).allMatch(e -> "produces".equals(e.type()) || "consumes".equals(e.type()));
        assertThat(dto.nodes().stream().filter(n -> "applicationGhost".equals(n.type()))).hasSize(1);

        // 5 distinct information object names across the 5 flows.
        assertThat(dto.nodes().stream().filter(n -> "informationObject".equals(n.type()))).hasSize(5);
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
