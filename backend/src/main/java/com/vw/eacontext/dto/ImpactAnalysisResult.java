package com.vw.eacontext.dto;

import java.util.List;
import java.util.Set;

/**
 * The blast radius of an application: every upstream and downstream application
 * reachable through interfaces, plus the connecting edges.
 *
 * @param appId      the application the analysis was run for
 * @param affected   all affected application ids (upstream + downstream + origin)
 * @param upstream   ids of applications that (transitively) feed the origin
 * @param downstream ids of applications the origin (transitively) feeds
 * @param edges      the interface edges connecting the affected applications
 */
public record ImpactAnalysisResult(
        String appId,
        Set<String> affected,
        Set<String> upstream,
        Set<String> downstream,
        List<GraphEdge> edges) {

    public ImpactAnalysisResult {
        affected = affected == null ? Set.of() : Set.copyOf(affected);
        upstream = upstream == null ? Set.of() : Set.copyOf(upstream);
        downstream = downstream == null ? Set.of() : Set.copyOf(downstream);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}

