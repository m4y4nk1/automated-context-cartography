package com.vw.eacontext.graph;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jgrapht.Graph;
import org.springframework.stereotype.Service;

import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.dto.ImpactAnalysisResult;
import com.vw.eacontext.exception.EaNotFoundException;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes the "blast radius" of an application: all upstream (feeding) and
 * downstream (dependent) applications reachable through relationships.
 *
 * <p>Uses directed traversal over the JGraphT graph produced by
 * {@link GraphBuilderService}: downstream follows edges in their natural
 * (source &rarr; target) direction, upstream follows them in reverse. Each
 * connecting edge is tagged with its relationship type
 * ({@code DEPENDS_ON}/{@code USES}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactAnalysisService {

    private final GraphBuilderService graphBuilderService;

    /**
     * Runs an impact analysis for the given application id.
     *
     * @param model the canonical model to analyze
     * @param appId the id of the application to assess
     * @return the affected application ids and the connecting edges
     * @throws EaNotFoundException if no application with {@code appId} exists
     */
    public ImpactAnalysisResult impactAnalysis(CanonicalModel model, String appId) {
        return impactAnalysis(graphBuilderService.build(model), appId);
    }

    /**
     * Runs an impact analysis reusing a pre-built graph.
     *
     * @param graph the pre-built application/relationship graph
     * @param appId the id of the application to assess
     * @return the affected application ids and the connecting edges
     * @throws EaNotFoundException if no application with {@code appId} exists
     */
    public ImpactAnalysisResult impactAnalysis(Graph<Application, RelationshipEdge> graph, String appId) {
        Application origin = graph.vertexSet().stream()
                .filter(a -> a.id() != null && a.id().equals(appId))
                .findFirst()
                .orElseThrow(() -> new EaNotFoundException("No application with id '" + appId + "'"));

        Set<RelationshipEdge> connectingEdges = new LinkedHashSet<>();

        // Downstream: follow outgoing edges (source depends on -> target).
        Set<Application> downstream = traverse(origin, connectingEdges,
                graph::outgoingEdgesOf, graph::getEdgeTarget);

        // Upstream: follow incoming edges in reverse.
        Set<Application> upstream = traverse(origin, connectingEdges,
                graph::incomingEdgesOf, graph::getEdgeSource);

        Set<String> downstreamIds = ids(downstream);
        Set<String> upstreamIds = ids(upstream);

        Set<String> affected = new LinkedHashSet<>();
        affected.add(origin.id());
        affected.addAll(upstreamIds);
        affected.addAll(downstreamIds);

        List<GraphEdge> edges = connectingEdges.stream()
                .map(e -> toGraphEdge(graph, e))
                .toList();

        log.info("Impact analysis for '{}': {} upstream, {} downstream, {} affected, {} edge(s)",
                appId, upstreamIds.size(), downstreamIds.size(), affected.size(), edges.size());

        return new ImpactAnalysisResult(origin.id(), affected, upstreamIds, downstreamIds, edges);
    }

    /**
     * BFS from the origin in a single direction, collecting reached vertices
     * (excluding the origin) and the edges traversed.
     */
    private Set<Application> traverse(Application origin,
                                     Set<RelationshipEdge> collectedEdges,
                                     Function<Application, Set<RelationshipEdge>> edgesOf,
                                     Function<RelationshipEdge, Application> nextVertex) {
        Set<Application> reached = new LinkedHashSet<>();
        Set<Application> visited = new LinkedHashSet<>();
        Deque<Application> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            Application current = queue.poll();
            for (RelationshipEdge edge : edgesOf.apply(current)) {
                collectedEdges.add(edge);
                Application neighbor = nextVertex.apply(edge);
                if (visited.add(neighbor)) {
                    reached.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        reached.remove(origin); // guard against self-loops
        return reached;
    }

    private Set<String> ids(Set<Application> apps) {
        Set<String> ids = new LinkedHashSet<>();
        for (Application app : apps) {
            ids.add(app.id());
        }
        return ids;
    }

    private GraphEdge toGraphEdge(Graph<Application, RelationshipEdge> graph, RelationshipEdge edge) {
        Map<String, Object> data = GraphNode.attrs();
        data.put("dependencyCriticality",
                edge.getDependencyCriticality() == null ? null : edge.getDependencyCriticality().name());
        String typeName = edge.getRelationshipType() == null ? null : edge.getRelationshipType().name();
        return new GraphEdge(
                edge.getRelationshipId(),
                graph.getEdgeSource(edge).id(),
                graph.getEdgeTarget(edge).id(),
                typeName,
                typeName,
                data);
    }
}
