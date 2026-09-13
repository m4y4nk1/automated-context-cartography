package com.vw.eacontext.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A frame-agnostic directed graph edge ready for the frontend (maps to a
 * Cytoscape {@code data} element with {@code source}/{@code target}).
 *
 * @param id     unique edge id within the frame
 * @param source source node id
 * @param target target node id
 * @param label  display label
 * @param type   edge kind (e.g. the interface type, {@code membership},
 *               {@code produces}, {@code consumes})
 * @param data   additional presentation/analytics attributes (values may be {@code null})
 */
public record GraphEdge(String id, String source, String target, String label, String type,
                        Map<String, Object> data) {

    public GraphEdge {
        // Null-tolerant unmodifiable copy (e.g. dataObject may be null).
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public GraphEdge(String id, String source, String target, String label, String type) {
        this(id, source, target, label, type, Map.of());
    }
}

