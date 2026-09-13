package com.vw.eacontext.insight;

import java.util.List;

import org.jgrapht.Graph;

import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

/**
 * Strategy interface for a single architectural-insight detector.
 *
 * <p>Spring autowires every bean implementing this interface into
 * {@link InsightService}, which simply runs each one and flattens the
 * results — adding a new detector never requires touching existing detector
 * code or {@code InsightService} itself.</p>
 */
public interface Detector {

    /**
     * @param model the parsed canonical model
     * @param graph the relationship-based application graph (applications as
     *              vertices, {@link com.vw.eacontext.model.Relationship} rows as edges)
     * @return findings produced by this detector (never {@code null}, may be empty)
     */
    List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph);
}
