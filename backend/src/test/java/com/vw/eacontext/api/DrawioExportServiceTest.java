package com.vw.eacontext.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.vw.eacontext.dto.DiagramExportRequest;
import com.vw.eacontext.dto.DiagramExportRequest.NodePlacement;
import com.vw.eacontext.dto.ExportFile;
import com.vw.eacontext.dto.Frame;
import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;

/**
 * Uses a hand-built graph rather than the sample dataset so the geometry and
 * escaping assertions can be exact.
 */
class DrawioExportServiceTest {

    private final DrawioExportService service = new DrawioExportService();

    /** A label with every character XML has to escape, to prove nothing is hand-concatenated. */
    private static final String NASTY_LABEL = "Sales & Ordering <x> \"y\"";

    private GraphDto graph() {
        return new GraphDto("application",
                List.of(
                        new GraphNode("APP-1", NASTY_LABEL, "application"),
                        new GraphNode("APP-2", "Second", "application"),
                        new GraphNode("APP-3", "Filtered out", "application")),
                List.of(
                        new GraphEdge("REL-1", "APP-1", "APP-2", "USES", "USES"),
                        new GraphEdge("REL-2", "APP-1", "APP-3", "DEPENDS_ON", "DEPENDS_ON")));
    }

    /** APP-3 is deliberately absent: the caller filtered it out on screen. */
    private DiagramExportRequest request() {
        Map<String, NodePlacement> placements = new LinkedHashMap<>();
        placements.put("APP-1", new NodePlacement(0, 0, 100, 50, "#4f9d8f"));
        placements.put("APP-2", new NodePlacement(300, 200, 100, 50, "#c98a3c"));
        return new DiagramExportRequest("application", placements);
    }

    private Document parse(byte[] content) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(content));
    }

    private List<Element> cells(Document doc, String kindAttribute) {
        NodeList all = doc.getElementsByTagName("mxCell");
        return java.util.stream.IntStream.range(0, all.getLength())
                .mapToObj(i -> (Element) all.item(i))
                .filter(el -> "1".equals(el.getAttribute(kindAttribute)))
                .toList();
    }

    @Test
    void producesWellFormedXmlNamedForTheFrame() throws Exception {
        ExportFile file = service.toDrawio(graph(), Frame.APPLICATION, request(), Set.of());

        assertThat(file.filename()).isEqualTo("ea-context-application.drawio");
        assertThat(file.contentType()).isEqualTo("application/xml");
        // Parsing is the assertion: malformed or unescaped XML throws here.
        Document doc = parse(file.content());
        assertThat(doc.getDocumentElement().getTagName()).isEqualTo("mxfile");
        assertThat(doc.getElementsByTagName("diagram").item(0).getAttributes()
                .getNamedItem("name").getNodeValue()).isEqualTo("Application");
    }

    @Test
    void exportsOnlyPlacedNodesAndTheEdgesBetweenThem() throws Exception {
        Document doc = parse(service.toDrawio(graph(), Frame.APPLICATION, request(), Set.of()).content());

        assertThat(cells(doc, "vertex")).extracting(el -> el.getAttribute("id"))
                .containsExactlyInAnyOrder("n-APP-1", "n-APP-2");
        // REL-2 points at the filtered-out APP-3, so it must not be emitted.
        assertThat(cells(doc, "edge")).extracting(el -> el.getAttribute("id"))
                .containsExactly("e-REL-1");
        assertThat(cells(doc, "edge").get(0).getAttribute("source")).isEqualTo("n-APP-1");
        assertThat(cells(doc, "edge").get(0).getAttribute("target")).isEqualTo("n-APP-2");
    }

    @Test
    void convertsCentreCoordinatesToTopLeftAndShiftsOntoThePage() throws Exception {
        Document doc = parse(service.toDrawio(graph(), Frame.APPLICATION, request(), Set.of()).content());

        // APP-1's centre (0,0) at 100x50 is top-left (-50,-25) — the minimum corner, so it
        // lands on the 40px margin, and everything else shifts by the same amount.
        Element first = cells(doc, "vertex").get(0);
        Element geometry = (Element) first.getElementsByTagName("mxGeometry").item(0);
        assertThat(geometry.getAttribute("x")).isEqualTo("40");
        assertThat(geometry.getAttribute("y")).isEqualTo("40");
        assertThat(geometry.getAttribute("width")).isEqualTo("100");

        // APP-2's centre (300,200) -> top-left (250,175), shifted by the same (+90,+65).
        Element second = (Element) cells(doc, "vertex").get(1).getElementsByTagName("mxGeometry").item(0);
        assertThat(second.getAttribute("x")).isEqualTo("340");
        assertThat(second.getAttribute("y")).isEqualTo("240");
    }

    @Test
    void escapesLabelsAndStylesEdgesAndFindingsLikeTheScreen() throws Exception {
        Document doc = parse(service.toDrawio(graph(), Frame.APPLICATION, request(), Set.of("APP-2")).content());

        List<Element> vertices = cells(doc, "vertex");
        assertThat(vertices.get(0).getAttribute("value")).isEqualTo(NASTY_LABEL);
        assertThat(vertices.get(0).getAttribute("style")).contains("fillColor=#4f9d8f");
        // Unflagged keeps the plain stroke; flagged gets the alert stroke.
        assertThat(vertices.get(0).getAttribute("style")).doesNotContain("#d64545");
        assertThat(vertices.get(1).getAttribute("style")).contains("strokeColor=#d64545", "strokeWidth=3");
        // USES is the softer dependency and is drawn dashed, matching the on-screen stylesheet.
        assertThat(cells(doc, "edge").get(0).getAttribute("style")).contains("dashed=1");
    }

    @Test
    void fallsBackToAGeneratedLayoutWhenNoPlacementsAreSent() throws Exception {
        DiagramExportRequest wholeFrame = new DiagramExportRequest("application", Map.of());

        Document doc = parse(service.toDrawio(graph(), Frame.APPLICATION, wholeFrame, Set.of()).content());

        // No placements means "export the whole frame", so the filtered-out node returns.
        assertThat(cells(doc, "vertex")).hasSize(3);
        assertThat(cells(doc, "edge")).hasSize(2);
    }

    @Test
    void flagsSensitiveInformationObjectsWithoutRedactingTheirLabel() throws Exception {
        GraphDto infoflow = new GraphDto("informationObject",
                List.of(new GraphNode("IO:Customer Data", "Customer Data", "informationObject",
                        Map.of("sensitive", true))),
                List.of());
        DiagramExportRequest request = new DiagramExportRequest("infoflow",
                Map.of("IO:Customer Data", new NodePlacement(0, 0, 100, 50, null)));

        Document doc = parse(service.toDrawio(infoflow, Frame.INFO_FLOW, request, Set.of()).content());

        // Flagged with a marker, not redacted — the name itself is still legible.
        assertThat(cells(doc, "vertex").get(0).getAttribute("value")).isEqualTo("🔒 Customer Data");
    }

    @Test
    void rejectsNonHexColoursRatherThanInjectingThemIntoTheStyleString() throws Exception {
        DiagramExportRequest tampered = new DiagramExportRequest("application",
                Map.of("APP-1", new NodePlacement(0, 0, 100, 50, "red;strokeColor=#000000;evil=1")));

        Document doc = parse(service.toDrawio(graph(), Frame.APPLICATION, tampered, Set.of()).content());

        assertThat(cells(doc, "vertex").get(0).getAttribute("style")).doesNotContain("evil=1");
    }
}
