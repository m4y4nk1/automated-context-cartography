package com.vw.eacontext.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.Test;

import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.DependencyCriticality;
import com.vw.eacontext.model.RelationshipType;

class CycleDetectionServiceTest {

    private final CycleDetectionService service = new CycleDetectionService();

    @Test
    void parallelRelationshipsBetweenTheSamePairDoNotBreakCycleDetection() {
        // A "depends on" and a "uses" row between A and B are two parallel edges —
        // JGraphT's Johnson algorithm rejects those outright, which used to fail
        // the whole upload.
        Application a = app("A");
        Application b = app("B");
        DirectedPseudograph<Application, RelationshipEdge> graph = graph(a, b);
        graph.addEdge(a, b, edge("REL-1", RelationshipType.DEPENDS_ON));
        graph.addEdge(a, b, edge("REL-2", RelationshipType.USES));
        graph.addEdge(b, a, edge("REL-3", RelationshipType.DEPENDS_ON));

        List<List<String>> cycles = service.findCycles(graph);

        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0)).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void anApplicationDependingOnItselfIsASingleApplicationCycle() {
        Application a = app("A");
        Application b = app("B");
        DirectedPseudograph<Application, RelationshipEdge> graph = graph(a, b);
        graph.addEdge(a, a, edge("REL-1", RelationshipType.DEPENDS_ON));
        graph.addEdge(a, b, edge("REL-2", RelationshipType.DEPENDS_ON));

        assertThat(service.findCycles(graph)).containsExactly(List.of("A"));
    }

    @Test
    void acyclicGraphHasNoCycles() {
        Application a = app("A");
        Application b = app("B");
        DirectedPseudograph<Application, RelationshipEdge> graph = graph(a, b);
        graph.addEdge(a, b, edge("REL-1", RelationshipType.DEPENDS_ON));
        graph.addEdge(a, b, edge("REL-2", RelationshipType.USES));

        assertThat(service.findCycles(graph)).isEmpty();
    }

    private static DirectedPseudograph<Application, RelationshipEdge> graph(Application... apps) {
        DirectedPseudograph<Application, RelationshipEdge> graph = new DirectedPseudograph<>(null, null, false);
        for (Application app : apps) {
            graph.addVertex(app);
        }
        return graph;
    }

    private static RelationshipEdge edge(String id, RelationshipType type) {
        return new RelationshipEdge(id, type, DependencyCriticality.HIGH);
    }

    private static Application app(String id) {
        return Application.builder().id(id).name(id).build();
    }
}
