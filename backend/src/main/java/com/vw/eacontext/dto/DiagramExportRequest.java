package com.vw.eacontext.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A request to export one observation frame as an interoperable diagram file
 * (draw.io XML or PlantUML).
 *
 * <p>The backend has no node coordinates of its own — layout is computed
 * client-side by Cytoscape — so the caller sends back what it rendered and the
 * backend turns it into the target format. That is what makes the download
 * match the diagram on screen.</p>
 *
 * <p>{@code placements} does double duty: it carries the geometry <em>and</em>
 * defines which nodes are in scope. Callers send only their visible nodes, so
 * active filters are respected without a separate parameter. An empty map means
 * "export the whole frame", in which case the exporter generates its own grid
 * layout.</p>
 *
 * @param frame      the frame slug to export (application, domain, process, infoflow)
 * @param placements node id -&gt; where and how that node is drawn, or empty for "everything"
 */
public record DiagramExportRequest(String frame, Map<String, NodePlacement> placements) {

    public DiagramExportRequest {
        placements = placements == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(placements));
    }

    /**
     * Where a node is drawn on the client.
     *
     * @param x     centre x in the client's model coordinates
     * @param y     centre y in the client's model coordinates
     * @param w     rendered width
     * @param h     rendered height
     * @param color rendered fill colour (e.g. {@code #4f9d8f}); may be {@code null}
     */
    public record NodePlacement(double x, double y, double w, double h, String color) {
    }
}
