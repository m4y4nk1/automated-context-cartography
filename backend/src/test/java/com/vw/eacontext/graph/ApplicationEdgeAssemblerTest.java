package com.vw.eacontext.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.ingestion.ExcelEaDataParser;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.DependencyCriticality;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.model.RelationshipType;

/**
 * Covers the application edges against the full Auriga workbook, and —
 * because this is meant to work for any uploaded dataset, not just that one —
 * against hand-built models that exercise the degenerate shapes.
 */
@SpringBootTest
class ApplicationEdgeAssemblerTest {

    @Autowired
    private ExcelEaDataParser excelParser;

    @Autowired
    private ApplicationEdgeAssembler assembler;

    @Autowired
    private GhostReferenceResolver ghostReferenceResolver;

    @Autowired
    private GraphProjectionService projectionService;

    private CanonicalModel auriga() throws Exception {
        try (InputStream in = new ClassPathResource("Auriga_Motors_Synthetic_EA_Dataset.xlsx").getInputStream()) {
            return excelParser.parse(in);
        }
    }

    @Test
    void everyRecordInTheRealWorkbookReachesTheDiagramAsItsOwnEdge() throws Exception {
        GraphDto dto = projectionService.applicationView(auriga());

        // 46 applications + placeholders for the 3 ghost ids that relationship,
        // interface or flow records point at. APP-9003 is referenced only by a
        // process mapping, so it belongs to the process frame, not this one.
        assertThat(dto.nodes()).hasSize(49);
        assertThat(dto.nodes().stream().filter(n -> "applicationGhost".equals(n.type()))
                .map(n -> n.id())).containsExactlyInAnyOrder("APP-9001", "APP-9002", "APP-9004");

        // 65 relationships + 38 interfaces + 30 flows = 133 records, each its own
        // edge now — nothing merged, nothing dropped.
        assertThat(dto.edges()).hasSize(133);
        assertThat(dto.edges()).extracting(GraphEdge::id).doesNotHaveDuplicates();
    }

    @Test
    void everyRecordBetweenABusyPairBecomesItsOwnLabelledEdge() throws Exception {
        GraphDto dto = projectionService.applicationView(auriga());

        // APP-0001 <-> APP-0031 is described four times over: two dependencies,
        // an interface and a flow. Each must show as its own edge with its own
        // specific label rather than one merged line.
        assertThat(edge(dto, "REL-0009").label()).isEqualTo("depends on");
        assertThat(edge(dto, "REL-0031").label()).isEqualTo("depends on");
        assertThat(edge(dto, "IF-0024").label()).isEqualTo("Lead Sync");
        assertThat(edge(dto, "FLOW-0024").label()).isEqualTo("Sales Lead");
    }

    @Test
    void processMappedOnlyToAMissingApplicationSurvives() throws Exception {
        GraphDto dto = projectionService.businessProcessView(auriga());

        // BP-12's single mapping row points at APP-9003, which has no application
        // row. Dropping that row used to erase the process from the frame.
        assertThat(dto.nodes()).anyMatch(n -> "BP-12".equals(n.id()));
        assertThat(dto.nodes()).anyMatch(n -> "APP-9003".equals(n.id())
                && "applicationGhost".equals(n.type()));
        assertThat(dto.nodes().stream().filter(n -> "process".equals(n.type()))).hasSize(12);
    }

    @Test
    void cleanDatasetProducesNoPlaceholdersAtAll() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(app("A"), app("B")))
                .relationships(List.of(dependency("REL-1", "A", "B")))
                .build();

        assertThat(ghostReferenceResolver.resolve(model).isEmpty()).isTrue();
        assertThat(projectionService.applicationView(model).nodes())
                .allMatch(n -> "application".equals(n.type()));
    }

    @Test
    void datasetWithOnlyRelationshipsStillProducesOneEdgePerRecord() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(app("A"), app("B"), app("C")))
                .relationships(List.of(dependency("REL-1", "A", "B"), dependency("REL-2", "B", "C")))
                .build();

        List<GraphEdge> edges = assembler.assemble(model, Set.of("A", "B", "C"));

        assertThat(edges).hasSize(2);
        assertThat(edges).allMatch(e -> "depends on".equals(e.label()));
        assertThat(edges).allMatch(e -> "DEPENDS_ON".equals(e.type()));
        // Flipped: drawn provider -> dependent, i.e. the record's target -> source.
        GraphEdge rel1 = edges.stream().filter(e -> "REL-1".equals(e.id())).findFirst().orElseThrow();
        assertThat(rel1.source()).isEqualTo("B");
        assertThat(rel1.target()).isEqualTo("A");
    }

    /**
     * The exact scenario that used to be called a "direction conflict": a
     * relationship recorded "A depends on B" and an interface recorded
     * "B provides to A" — the same real-world coupling stated from opposite
     * ends. Un-flipped, these would draw as opposing arrows (A->B vs B->A).
     * With the relationship flipped to provider -> dependent, both now draw
     * the same way (B->A), which is the point of the fix: an arrowhead means
     * the same thing regardless of which edge type it belongs to.
     */
    @Test
    void relationshipAndInterfaceDescribingTheSameCouplingNowPointTheSameWay() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(app("A"), app("B")))
                .relationships(List.of(dependency("REL-1", "A", "B")))
                .interfaces(List.of(Interface.builder()
                        .id("IF-1").name("Pricing Lookup").providerApplicationId("B")
                        .consumerApplicationId("A").build()))
                .build();

        List<GraphEdge> edges = assembler.assemble(model, Set.of("A", "B"));

        assertThat(edges).hasSize(2);
        GraphEdge relationshipEdge = edges.stream().filter(e -> "REL-1".equals(e.id())).findFirst().orElseThrow();
        GraphEdge interfaceEdge = edges.stream().filter(e -> "IF-1".equals(e.id())).findFirst().orElseThrow();

        assertThat(relationshipEdge.source()).isEqualTo(interfaceEdge.source()).isEqualTo("B");
        assertThat(relationshipEdge.target()).isEqualTo(interfaceEdge.target()).isEqualTo("A");
        assertThat(interfaceEdge.label()).isEqualTo("Pricing Lookup");
    }

    @Test
    void blankAndUnknownEndpointsAreSkippedRatherThanThrowing() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(app("A"), app("B")))
                .relationships(List.of(
                        dependency("REL-1", "A", "B"),
                        dependency("REL-2", "A", null),
                        dependency("REL-3", "  ", "B")))
                .informationObjects(List.of(InformationObject.builder()
                        .id("FLOW-1").informationObject("Orphaned").sourceApplicationId("A")
                        .targetApplicationId("NOT-A-NODE").build()))
                .build();

        List<GraphEdge> edges = assembler.assemble(model, Set.of("A", "B"));

        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).id()).isEqualTo("REL-1");
        assertThat(edges.get(0).data().get("memberIds")).isEqualTo(List.of("REL-1"));
    }

    private static GraphEdge edge(GraphDto dto, String id) {
        return dto.edges().stream().filter(e -> id.equals(e.id())).findFirst().orElseThrow();
    }

    private static Application app(String id) {
        return Application.builder().id(id).name("App " + id).build();
    }

    private static Relationship dependency(String id, String source, String target) {
        return Relationship.builder()
                .id(id)
                .sourceApplicationId(source)
                .targetApplicationId(target)
                .relationshipType(RelationshipType.DEPENDS_ON)
                .dependencyCriticality(DependencyCriticality.HIGH)
                .build();
    }
}
