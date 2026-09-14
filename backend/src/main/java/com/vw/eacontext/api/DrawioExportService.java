package com.vw.eacontext.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.stereotype.Service;

import com.vw.eacontext.dto.DiagramExportRequest;
import com.vw.eacontext.dto.DiagramExportRequest.NodePlacement;
import com.vw.eacontext.dto.ExportFile;
import com.vw.eacontext.dto.Frame;
import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.exception.EaIngestionException;

/**
 * Renders an observation frame as a draw.io (diagrams.net) {@code .drawio} file
 * — an uncompressed {@code mxfile} XML document, which draw.io desktop/web and
 * the Confluence draw.io plugin both open directly.
 *
 * <p>Geometry comes from the caller (see {@link DiagramExportRequest}), so the
 * exported diagram matches the arrangement on screen rather than being
 * re-laid-out downstream.</p>
 */
@Service
public class DrawioExportService {

    /** Cell ids draw.io reserves for the model root and the default layer. */
    private static final String ROOT_CELL_ID = "0";
    private static final String LAYER_CELL_ID = "1";

    private static final double MARGIN = 40;
    private static final double FALLBACK_WIDTH = 160;
    private static final double FALLBACK_HEIGHT = 60;
    private static final double FALLBACK_GAP_X = 220;
    private static final double FALLBACK_GAP_Y = 120;

    private static final String DEFAULT_FILL = "#0E4A47";
    private static final String FLAGGED_STROKE = "#d64545";

    /** Colours are interpolated into a draw.io style string, so only accept literal hex. */
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{3,8}");

    /**
     * @param graph             the projected frame to export
     * @param frame             the frame being exported (drives the file and page name)
     * @param request           the caller's rendered geometry; empty placements means "whole frame"
     * @param flaggedEntityIds  entity ids carrying at least one finding, drawn with an alert stroke
     * @return the {@code .drawio} file
     */
    public ExportFile toDrawio(GraphDto graph, Frame frame, DiagramExportRequest request,
                               Set<String> flaggedEntityIds) {
        Map<String, Box> boxes = layOut(graph.nodes(), request.placements());
        byte[] content = write(graph, frame, boxes, flaggedEntityIds == null ? Set.of() : flaggedEntityIds);
        return new ExportFile("ea-context-" + frame.slug() + ".drawio", "application/xml", content);
    }

    /**
     * Resolves every in-scope node to a draw.io box.
     *
     * <p>Two conversions matter here. Cytoscape reports a node's <em>centre</em>
     * while {@code mxGeometry} wants its <em>top-left</em>, and client model
     * coordinates are usually centred on the origin (so they go negative) while
     * a draw.io page starts at 0,0 — hence the shift onto a positive canvas.</p>
     */
    private Map<String, Box> layOut(List<GraphNode> nodes, Map<String, NodePlacement> placements) {
        Map<String, Box> boxes = new LinkedHashMap<>();
        int fallbackIndex = 0;
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, nodes.size()))));

        for (GraphNode node : nodes) {
            NodePlacement placement = placements.get(node.id());
            if (placement == null) {
                if (!placements.isEmpty()) {
                    continue; // Out of scope: the caller filtered this node out.
                }
                double x = (fallbackIndex % columns) * FALLBACK_GAP_X;
                double y = (fallbackIndex / columns) * FALLBACK_GAP_Y;
                boxes.put(node.id(), new Box(x, y, FALLBACK_WIDTH, FALLBACK_HEIGHT, null));
                fallbackIndex++;
                continue;
            }
            double width = placement.w() > 0 ? placement.w() : FALLBACK_WIDTH;
            double height = placement.h() > 0 ? placement.h() : FALLBACK_HEIGHT;
            boxes.put(node.id(), new Box(
                    placement.x() - width / 2, placement.y() - height / 2, width, height, placement.color()));
        }

        double minX = boxes.values().stream().mapToDouble(Box::x).min().orElse(0);
        double minY = boxes.values().stream().mapToDouble(Box::y).min().orElse(0);
        double shiftX = MARGIN - minX;
        double shiftY = MARGIN - minY;
        boxes.replaceAll((id, box) -> box.shifted(shiftX, shiftY));
        return boxes;
    }

    private byte[] write(GraphDto graph, Frame frame, Map<String, Box> boxes, Set<String> flagged) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newFactory()
                    .createXMLStreamWriter(out, StandardCharsets.UTF_8.name());
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");

            xml.writeStartElement("mxfile");
            xml.writeAttribute("host", "context-cartography");
            xml.writeStartElement("diagram");
            xml.writeAttribute("id", frame.slug());
            xml.writeAttribute("name", frame.label());
            xml.writeStartElement("mxGraphModel");
            xml.writeAttribute("grid", "1");
            xml.writeAttribute("gridSize", "10");
            xml.writeAttribute("page", "1");
            xml.writeAttribute("pageWidth", "1169");
            xml.writeAttribute("pageHeight", "826");
            xml.writeStartElement("root");
            xml.writeCharacters("\n");

            // draw.io requires these two: the model root and the default layer every cell parents to.
            writeEmptyCell(xml, ROOT_CELL_ID, null);
            writeEmptyCell(xml, LAYER_CELL_ID, ROOT_CELL_ID);

            for (GraphNode node : graph.nodes()) {
                Box box = boxes.get(node.id());
                if (box == null) {
                    continue;
                }
                writeVertex(xml, node, box, flagged.contains(node.id()));
            }
            for (GraphEdge edge : graph.edges()) {
                // Only connect nodes that actually made it into the diagram.
                if (!boxes.containsKey(edge.source()) || !boxes.containsKey(edge.target())) {
                    continue;
                }
                writeEdge(xml, edge);
            }

            xml.writeEndElement(); // root
            xml.writeEndElement(); // mxGraphModel
            xml.writeEndElement(); // diagram
            xml.writeEndElement(); // mxfile
            xml.writeEndDocument();
            xml.flush();
            xml.close();
        } catch (XMLStreamException e) {
            throw new EaIngestionException("Failed to generate draw.io export", e);
        }
        return out.toByteArray();
    }

    private void writeEmptyCell(XMLStreamWriter xml, String id, String parent) throws XMLStreamException {
        xml.writeEmptyElement("mxCell");
        xml.writeAttribute("id", id);
        if (parent != null) {
            xml.writeAttribute("parent", parent);
        }
        xml.writeCharacters("\n");
    }

    private void writeVertex(XMLStreamWriter xml, GraphNode node, Box box, boolean flagged)
            throws XMLStreamException {
        xml.writeStartElement("mxCell");
        xml.writeAttribute("id", "n-" + node.id());
        xml.writeAttribute("value", label(node));
        xml.writeAttribute("style", vertexStyle(box.color(), flagged));
        xml.writeAttribute("vertex", "1");
        xml.writeAttribute("parent", LAYER_CELL_ID);

        xml.writeEmptyElement("mxGeometry");
        xml.writeAttribute("x", number(box.x()));
        xml.writeAttribute("y", number(box.y()));
        xml.writeAttribute("width", number(box.width()));
        xml.writeAttribute("height", number(box.height()));
        xml.writeAttribute("as", "geometry");

        xml.writeEndElement(); // mxCell
        xml.writeCharacters("\n");
    }

    private void writeEdge(XMLStreamWriter xml, GraphEdge edge) throws XMLStreamException {
        xml.writeStartElement("mxCell");
        xml.writeAttribute("id", "e-" + edge.id());
        xml.writeAttribute("value", edge.label() == null ? "" : edge.label());
        xml.writeAttribute("style", edgeStyle(edge.type()));
        xml.writeAttribute("edge", "1");
        xml.writeAttribute("parent", LAYER_CELL_ID);
        xml.writeAttribute("source", "n-" + edge.source());
        xml.writeAttribute("target", "n-" + edge.target());

        xml.writeEmptyElement("mxGeometry");
        xml.writeAttribute("relative", "1");
        xml.writeAttribute("as", "geometry");

        xml.writeEndElement(); // mxCell
        xml.writeCharacters("\n");
    }

    /**
     * The node's label, prefixed with a lock marker for a Confidential/PII/PCI
     * information object ({@code GraphProjectionService.informationFlowView}
     * already computes {@code data.sensitive}). Flagged, not redacted — an
     * architect needs to see what a flow carries to act on the finding; this
     * only marks it, mirroring the on-screen sensitive-flow ring.
     */
    private String label(GraphNode node) {
        String text = node.label() == null ? node.id() : node.label();
        return Boolean.TRUE.equals(node.data().get("sensitive")) ? "🔒 " + text : text;
    }

    private String vertexStyle(String color, boolean flagged) {
        String fill = color != null && HEX_COLOR.matcher(color).matches() ? color : DEFAULT_FILL;
        StringBuilder style = new StringBuilder("rounded=1;whiteSpace=wrap;html=1;fontColor=#ffffff;");
        style.append("fillColor=").append(fill).append(';');
        // Keep anything the insight engine flagged visually flagged downstream too.
        style.append(flagged
                ? "strokeColor=" + FLAGGED_STROKE + ";strokeWidth=3;"
                : "strokeColor=#ffffff;");
        return style.toString();
    }

    /**
     * Mirrors the on-screen convention: {@code USES} and flow-only couplings are
     * drawn dashed, and the two edge kinds that exist without a declared
     * dependency keep their distinguishing colour so the exported diagram reads
     * the same way the canvas does.
     */
    private String edgeStyle(String type) {
        String base = "edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;";
        return switch (type == null ? "" : type) {
            case "USES" -> base + "dashed=1;";
            case "INTERFACE" -> base + "strokeColor=#3f8fa8;";
            case "FLOW" -> base + "dashed=1;strokeColor=#8a63c7;";
            default -> base;
        };
    }

    /** Whole numbers render as {@code 120} rather than {@code 120.0}. */
    private String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** A node's resolved draw.io geometry (top-left origin) and fill. */
    private record Box(double x, double y, double width, double height, String color) {
        Box shifted(double dx, double dy) {
            return new Box(x + dx, y + dy, width, height, color);
        }
    }
}
