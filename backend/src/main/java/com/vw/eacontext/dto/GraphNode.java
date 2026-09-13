package com.vw.eacontext.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A frame-agnostic graph node ready for the frontend (maps to a Cytoscape
 * {@code data} element).
 *
 * @param id    unique node id within the frame
 * @param label display label
 * @param type  node kind (e.g. {@code application}, {@code domain},
 *              {@code process}, {@code informationObject})
 * @param data  additional presentation/analytics attributes (values may be {@code null})
 */
public record GraphNode(String id, String label, String type, Map<String, Object> data) {

    public GraphNode {
        // Null-tolerant unmodifiable copy (attributes such as owner may be null).
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public GraphNode(String id, String label, String type) {
        this(id, label, type, Map.of());
    }

    /** Mutable-builder-style convenience for accumulating attributes. */
    public static Map<String, Object> attrs() {
        return new LinkedHashMap<>();
    }
}

