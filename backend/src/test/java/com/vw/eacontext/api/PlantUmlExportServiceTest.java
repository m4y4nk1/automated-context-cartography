package com.vw.eacontext.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.dto.DiagramExportRequest;
import com.vw.eacontext.dto.DiagramExportRequest.NodePlacement;
import com.vw.eacontext.dto.ExportFile;
import com.vw.eacontext.dto.Frame;
import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;

class PlantUmlExportServiceTest {

    private final PlantUmlExportService service = new PlantUmlExportService();

    private GraphDto graph() {
        return new GraphDto("application",
                List.of(
                        new GraphNode("APP-1", "CRM \"Suite\"", "application"),
                        // Sanitizes to the same alias as APP-1 would if ids collided on '_'.
                        new GraphNode("APP_1", "Collides on sanitize", "application"),
                        new GraphNode("Sales & Ordering", "Sales & Ordering (9)", "domain"),
                        new GraphNode("APP-3", "Filtered out", "application")),
                List.of(
                        new GraphEdge("REL-1", "APP-1", "APP_1", "USES", "USES"),
                        new GraphEdge("REL-2", "APP-1", "Sales & Ordering", "DEPENDS_ON", "DEPENDS_ON"),
                        new GraphEdge("REL-3", "APP-1", "APP-3", "DEPENDS_ON", "DEPENDS_ON")));
    }

    private DiagramExportRequest request() {
        Map<String, NodePlacement> placements = new LinkedHashMap<>();
        placements.put("APP-1", new NodePlacement(0, 0, 100, 50, null));
        placements.put("APP_1", new NodePlacement(100, 0, 100, 50, null));
        placements.put("Sales & Ordering", new NodePlacement(200, 0, 100, 50, null));
        return new DiagramExportRequest("application", placements);
    }

    private String export(DiagramExportRequest request) {
        ExportFile file = service.toPlantUml(graph(), Frame.APPLICATION, request);
        assertThat(file.filename()).isEqualTo("ea-context-application.puml");
        assertThat(file.contentType()).isEqualTo("text/plain");
        return new String(file.content(), StandardCharsets.UTF_8);
    }

    @Test
    void emitsAWellFormedDiagramWithOneDeclarationPerNode() {
        String puml = export(request());

        assertThat(puml).startsWith("@startuml").endsWith("@enduml\n");
        assertThat(puml).contains("component \"CRM 'Suite'\" as APP_1");
        // Node type drives the PlantUML keyword.
        assertThat(puml).contains("rectangle \"Sales & Ordering (9)\" as Sales___Ordering");
        // Filtered out on screen, so absent here too.
        assertThat(puml).doesNotContain("Filtered out");
    }

    @Test
    void keepsAliasesUniqueWhenTwoIdsSanitizeToTheSameToken() {
        String puml = export(request());

        // "APP-1" and "APP_1" both normalize to APP_1; the second gets a suffix.
        assertThat(puml).contains("as APP_1\n");
        assertThat(puml).contains("as APP_1_2\n");
    }

    @Test
    void drawsUsesDependenciesDashedAndLabelsTheRest() {
        String puml = export(request());

        assertThat(puml).contains("APP_1 ..> APP_1_2 : USES");
        assertThat(puml).contains("APP_1 --> Sales___Ordering : DEPENDS_ON");
        // REL-3's target was filtered out, so the edge is dropped rather than dangling.
        assertThat(puml).doesNotContain("REL-3");
        assertThat(puml.lines().filter(line -> line.contains("-->") || line.contains("..>"))).hasSize(2);
    }

    @Test
    void exportsTheWholeFrameWhenNoPlacementsAreSent() {
        String puml = export(new DiagramExportRequest("application", Map.of()));

        assertThat(puml).contains("Filtered out");
        assertThat(puml.lines().filter(line -> line.contains("-->") || line.contains("..>"))).hasSize(3);
    }

    @Test
    void flagsSensitiveInformationObjectsWithoutRedactingTheirLabel() {
        GraphDto infoflow = new GraphDto("informationObject",
                List.of(new GraphNode("IO:Customer Data", "Customer Data", "informationObject",
                        Map.of("sensitive", true))),
                List.of());
        DiagramExportRequest request = new DiagramExportRequest("infoflow",
                Map.of("IO:Customer Data", new NodePlacement(0, 0, 100, 50, null)));

        ExportFile file = service.toPlantUml(infoflow, Frame.INFO_FLOW, request);
        String puml = new String(file.content(), StandardCharsets.UTF_8);

        assertThat(puml).contains("🔒 Customer Data");
    }
}
