package com.vw.eacontext.graph;

import java.util.ArrayList;
import java.util.List;

import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.stereotype.Service;

import com.vw.eacontext.model.Application;

/**
 * Circular-dependency detection over the {@link com.vw.eacontext.model.Relationship}-based
 * application graph, backed by JGraphT's cycle algorithms rather than a
 * hand-rolled traversal.
 *
 * <p>The relationship graph is a pseudograph: two relationship rows between the
 * same pair of applications (a "depends on" and a "uses", say) are two parallel
 * edges, and an application can be recorded as depending on itself. But
 * {@link JohnsonSimpleCycles} rejects parallel edges outright. A cycle is about
 * which applications reach which, not how many rows connect them, so the search
 * runs on a simple copy — one edge per ordered pair — and self-loops are
 * reported directly as single-application cycles.</p>
 */
@Service
public class CycleDetectionService {

    /** @return {@code true} when the graph contains at least one directed cycle. */
    public boolean hasCycle(Graph<Application, RelationshipEdge> graph) {
        return new CycleDetector<>(graph).detectCycles();
    }

    /**
     * Every distinct simple cycle in the graph, as ordered lists of
     * application ids (each list's first id is not repeated at the end).
     * Empty when the graph is acyclic.
     */
    public List<List<String>> findCycles(Graph<Application, RelationshipEdge> graph) {
        if (!hasCycle(graph)) {
            return List.of();
        }
        List<List<String>> cycles = new ArrayList<>();
        Graph<Application, DefaultEdge> simple = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (Application app : graph.vertexSet()) {
            simple.addVertex(app);
            if (graph.containsEdge(app, app)) {
                cycles.add(List.of(app.id()));
            }
        }
        for (RelationshipEdge edge : graph.edgeSet()) {
            Application source = graph.getEdgeSource(edge);
            Application target = graph.getEdgeTarget(edge);
            if (!source.equals(target) && !simple.containsEdge(source, target)) {
                simple.addEdge(source, target);
            }
        }

        for (List<Application> cycle : new JohnsonSimpleCycles<>(simple).findSimpleCycles()) {
            cycles.add(cycle.stream().map(Application::id).toList());
        }
        return cycles;
    }
}
