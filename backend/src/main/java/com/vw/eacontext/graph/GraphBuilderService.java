package com.vw.eacontext.graph;

import java.util.HashMap;
import java.util.Map;

import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedPseudograph;
import org.springframework.stereotype.Service;

import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Relationship;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds a JGraphT graph from a {@link CanonicalModel}.
 *
 * <p>Applications become vertices and {@link Relationship} rows become
 * directed edges from the source (dependent) application to the target
 * (depended-upon) application. A {@link DirectedPseudograph} is used so that
 * self-loops and multiple parallel edges (several relationships between the
 * same two applications) are supported. Each edge carries its relationship
 * metadata via {@link RelationshipEdge}.</p>
 *
 * <p>{@code Interfaces} are not graph edges in this primary graph — they are
 * read directly off the model where needed (the information-flow projection,
 * the deprecated-interface and interface-without-relationship detectors),
 * since none of those consumers require graph algorithms over interfaces.</p>
 */
@Slf4j
@Service
public class GraphBuilderService {

    /**
     * Converts the canonical model into a directed pseudograph of applications
     * connected by relationships.
     *
     * <p>Relationships whose source or target cannot be resolved to a known
     * application are skipped (and logged); use the validation service and the
     * broken-reference detectors to surface such issues to the user.</p>
     *
     * @param model the canonical model (must not be {@code null})
     * @return a directed pseudograph with applications as vertices and
     *         {@link RelationshipEdge}s as edges
     */
    public Graph<Application, RelationshipEdge> build(CanonicalModel model) {
        DirectedPseudograph<Application, RelationshipEdge> graph =
                new DirectedPseudograph<>(null, null, false);

        Map<String, Application> byId = new HashMap<>();
        for (Application app : model.applications()) {
            if (app.id() == null || app.id().isBlank()) {
                log.warn("Skipping application with blank id: {}", app);
                continue;
            }
            if (byId.putIfAbsent(app.id(), app) == null) {
                graph.addVertex(app);
            } else {
                log.warn("Skipping duplicate application id '{}'", app.id());
            }
        }

        int added = 0;
        for (Relationship relationship : model.relationships()) {
            Application source = byId.get(relationship.sourceApplicationId());
            Application target = byId.get(relationship.targetApplicationId());
            if (source == null || target == null) {
                log.warn("Skipping relationship '{}': unresolved source '{}' or target '{}'",
                        relationship.id(), relationship.sourceApplicationId(), relationship.targetApplicationId());
                continue;
            }
            RelationshipEdge edge = new RelationshipEdge(
                    relationship.id(), relationship.relationshipType(), relationship.dependencyCriticality());
            graph.addEdge(source, target, edge);
            added++;
        }

        log.info("Built graph: {} application node(s), {} relationship edge(s)",
                graph.vertexSet().size(), added);
        return graph;
    }
}
