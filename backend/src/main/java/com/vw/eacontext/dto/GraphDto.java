package com.vw.eacontext.dto;

import java.util.List;

/**
 * A frame-agnostic graph projection (nodes + edges) ready for the frontend.
 *
 * <p>The same shape is produced for every observation frame (application,
 * business process, domain, information flow); the {@code frame} field names
 * which projection this is.</p>
 *
 * @param frame the observation frame this projection represents
 * @param nodes the projected nodes
 * @param edges the projected edges
 */
public record GraphDto(String frame, List<GraphNode> nodes, List<GraphEdge> edges) {

    public GraphDto {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}

