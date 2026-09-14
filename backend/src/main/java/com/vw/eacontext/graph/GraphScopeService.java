package com.vw.eacontext.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.GraphEdge;
import com.vw.eacontext.dto.GraphNode;
import com.vw.eacontext.exception.EaNotFoundException;

/**
 * Reduces a full {@link GraphDto} down to one anchor's neighborhood — a
 * genuinely scoped context diagram rather than the whole projected frame with
 * some elements dimmed.
 *
 * <p>{@link GraphDto} is already frame-agnostic (any node/edge shape), so one
 * generic breadth-first reduction works for every frame without per-frame
 * special-casing: build an undirected adjacency map from the frame's own
 * edges, then expand outward from a seed set up to {@code depth} hops.
 * Undirected on purpose — a context diagram wants "what's connected to X,"
 * providers and consumers alike, not a one-directional reachability walk.</p>
 */
@Service
public class GraphScopeService {

    /**
     * Scopes {@code full} to one anchor's neighborhood — a node, or (falling
     * back) an edge.
     *
     * <p>The fallback matters for interfaces: since the application frame's
     * edges are built one-per-record, an Interface is an edge with no node of
     * its own (see {@link ApplicationEdgeAssembler}). Anchoring on one seeds
     * the BFS from both its endpoints, answering "who provides, who consumes"
     * for that interface directly — the observation-frame prompt "identify
     * consumers of a given interface / provider" has no other node to anchor
     * on.</p>
     *
     * @param full     the complete frame projection
     * @param anchorId the node id, or failing that the edge id, to anchor on
     * @param depth    how many hops out from the anchor to include (&ge; 0)
     * @return a new {@link GraphDto} containing only the reached nodes, and
     *         only the edges whose both endpoints were reached
     * @throws EaNotFoundException if {@code anchorId} is neither a node nor an
     *                              edge in {@code full}
     */
    public GraphDto scope(GraphDto full, String anchorId, int depth) {
        boolean isNode = full.nodes().stream().anyMatch(n -> anchorId.equals(n.id()));
        if (isNode) {
            return reduce(full, Set.of(anchorId), depth);
        }
        Optional<GraphEdge> edge = full.edges().stream().filter(e -> anchorId.equals(e.id())).findFirst();
        if (edge.isPresent()) {
            return reduce(full, Set.of(edge.get().source(), edge.get().target()), depth);
        }
        throw new EaNotFoundException("No node or edge with id '" + anchorId + "' in this frame");
    }

    /**
     * Scopes {@code full} to the neighborhood of every node matching
     * {@code seedPredicate} — used for a domain anchor, where the seed is every
     * application in that domain rather than a single id.
     *
     * @throws EaNotFoundException if no node matches {@code seedPredicate}
     */
    public GraphDto scopeByAttribute(GraphDto full, Predicate<GraphNode> seedPredicate, int depth) {
        Set<String> seeds = new LinkedHashSet<>();
        for (GraphNode node : full.nodes()) {
            if (seedPredicate.test(node)) {
                seeds.add(node.id());
            }
        }
        if (seeds.isEmpty()) {
            throw new EaNotFoundException("No node in this frame matches the requested anchor");
        }
        return reduce(full, seeds, depth);
    }

    private GraphDto reduce(GraphDto full, Set<String> seeds, int depth) {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (GraphEdge edge : full.edges()) {
            adjacency.computeIfAbsent(edge.source(), id -> new ArrayList<>()).add(edge.target());
            adjacency.computeIfAbsent(edge.target(), id -> new ArrayList<>()).add(edge.source());
        }

        Set<String> reached = new LinkedHashSet<>(seeds);
        Deque<String> frontier = new ArrayDeque<>(seeds);
        for (int hop = 0; hop < depth && !frontier.isEmpty(); hop++) {
            Deque<String> next = new ArrayDeque<>();
            while (!frontier.isEmpty()) {
                for (String neighbor : adjacency.getOrDefault(frontier.poll(), List.of())) {
                    if (reached.add(neighbor)) {
                        next.add(neighbor);
                    }
                }
            }
            frontier = next;
        }

        List<GraphNode> nodes = full.nodes().stream().filter(n -> reached.contains(n.id())).toList();
        List<GraphEdge> edges = full.edges().stream()
                .filter(e -> reached.contains(e.source()) && reached.contains(e.target()))
                .toList();
        return new GraphDto(full.frame(), nodes, edges);
    }
}
