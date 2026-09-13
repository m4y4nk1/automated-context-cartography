package com.vw.eacontext.graph;

import java.util.ArrayList;
import java.util.List;

import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.springframework.stereotype.Service;

import com.vw.eacontext.model.Application;

/**
 * Circular-dependency detection over the {@link com.vw.eacontext.model.Relationship}-based
 * application graph, backed by JGraphT's cycle algorithms rather than a
 * hand-rolled traversal.
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
        JohnsonSimpleCycles<Application, RelationshipEdge> johnson = new JohnsonSimpleCycles<>(graph);
        List<List<String>> cycles = new ArrayList<>();
        for (List<Application> cycle : johnson.findSimpleCycles()) {
            List<String> ids = new ArrayList<>();
            for (Application app : cycle) {
                ids.add(app.id());
            }
            cycles.add(ids);
        }
        return cycles;
    }
}
