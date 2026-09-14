package com.vw.eacontext.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.exception.EaNotFoundException;

class GraphScopeServiceTest {

    private final GraphScopeService service = new GraphScopeService();

    /** A -> B -> C -> D -> E chain, plus an isolated node F, for depth tests. */
    private GraphDto chain() {
        return new GraphDto("application",
                List.of(
                        new GraphNode("A", "A", "application"),
                        new GraphNode("B", "B", "application"),
                        new GraphNode("C", "C", "application"),
                        new GraphNode("D", "D", "application"),
                        new GraphNode("E", "E", "application"),
                        new GraphNode("F", "F", "application")),
                List.of(
                        new GraphEdge("e1", "A", "B", null, "DEPENDS_ON"),
                        new GraphEdge("e2", "B", "C", null, "DEPENDS_ON"),
                        new GraphEdge("e3", "C", "D", null, "DEPENDS_ON"),
                        new GraphEdge("e4", "D", "E", null, "DEPENDS_ON")));
    }

    @Test
    void depthZeroReturnsOnlyTheAnchor() {
        GraphDto scoped = service.scope(chain(), "C", 0);

        assertThat(scoped.nodes()).extracting(GraphNode::id).containsExactly("C");
        assertThat(scoped.edges()).isEmpty();
    }

    @Test
    void depthOneIncludesBothDirections() {
        // Edges are undirected for scoping purposes: a context diagram wants
        // everything connected to C, not just what C depends on.
        GraphDto scoped = service.scope(chain(), "C", 1);

        assertThat(scoped.nodes()).extracting(GraphNode::id).containsExactlyInAnyOrder("B", "C", "D");
        assertThat(scoped.edges()).extracting(GraphEdge::id).containsExactlyInAnyOrder("e2", "e3");
    }

    @Test
    void depthTwoExpandsFurtherButNeverReachesTheIsolatedNode() {
        GraphDto scoped = service.scope(chain(), "C", 2);

        assertThat(scoped.nodes()).extracting(GraphNode::id).containsExactlyInAnyOrder("A", "B", "C", "D", "E");
        assertThat(scoped.nodes()).extracting(GraphNode::id).doesNotContain("F");
        assertThat(scoped.edges()).hasSize(4);
    }

    @Test
    void unknownAnchorThrowsNotFound() {
        assertThatThrownBy(() -> service.scope(chain(), "ZZZ", 1))
                .isInstanceOf(EaNotFoundException.class);
    }

    /**
     * "e2" is an edge id (B<->C), not a node id — this is exactly the shape an
     * Interface anchor takes in the application frame. The node check should
     * miss, then the edge fallback should seed from both of e2's endpoints.
     */
    @Test
    void anchoringOnAnEdgeIdSeedsFromBothEndpoints() {
        GraphDto scoped = service.scope(chain(), "e2", 0);

        assertThat(scoped.nodes()).extracting(GraphNode::id).containsExactlyInAnyOrder("B", "C");
        assertThat(scoped.edges()).extracting(GraphEdge::id).containsExactly("e2");
    }

    @Test
    void anchoringOnAnEdgeIdExpandsFurtherAtDeeperDepth() {
        GraphDto scoped = service.scope(chain(), "e2", 1);

        // One hop out from both B and C reaches A and D too.
        assertThat(scoped.nodes()).extracting(GraphNode::id).containsExactlyInAnyOrder("A", "B", "C", "D");
    }

    @Test
    void anchorMatchingNeitherANodeNorAnEdgeThrowsNotFound() {
        assertThatThrownBy(() -> service.scope(chain(), "NOT-A-NODE-OR-EDGE", 1))
                .isInstanceOf(EaNotFoundException.class);
    }

    @Test
    void scopeByAttributeSeedsFromEveryMatchingNode() {
        GraphDto graph = new GraphDto("application",
                List.of(
                        new GraphNode("X", "X", "application", java.util.Map.of("businessDomain", "Sales")),
                        new GraphNode("Y", "Y", "application", java.util.Map.of("businessDomain", "Sales")),
                        new GraphNode("Z", "Z", "application", java.util.Map.of("businessDomain", "Finance"))),
                List.of(new GraphEdge("e1", "Y", "Z", null, "DEPENDS_ON")));

        // Seeded from both Sales apps (X, Y); depth 1 reaches Z via Y.
        GraphDto scoped = service.scopeByAttribute(graph, n -> "Sales".equals(n.data().get("businessDomain")), 1);

        assertThat(scoped.nodes()).extracting(GraphNode::id).containsExactlyInAnyOrder("X", "Y", "Z");
    }

    @Test
    void scopeByAttributeWithNoMatchesThrowsNotFound() {
        GraphDto graph = new GraphDto("application",
                List.of(new GraphNode("X", "X", "application", java.util.Map.of("businessDomain", "Sales"))),
                List.of());

        assertThatThrownBy(() -> service.scopeByAttribute(graph, n -> "Unknown".equals(n.data().get("businessDomain")), 1))
                .isInstanceOf(EaNotFoundException.class);
    }
}
