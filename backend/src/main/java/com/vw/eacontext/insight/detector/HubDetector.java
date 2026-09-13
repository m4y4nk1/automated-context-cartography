package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.List;

import org.jgrapht.Graph;
import org.springframework.stereotype.Component;

import com.vw.eacontext.config.InsightProperties;
import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.insight.Detector;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.validation.Severity;

import lombok.RequiredArgsConstructor;

/**
 * Single point of failure / hub: an application whose in-degree in the
 * Relationships graph (how many other applications depend on it) exceeds the
 * configured threshold.
 */
@Component
@RequiredArgsConstructor
public class HubDetector implements Detector {

    private final InsightProperties properties;

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        int threshold = properties.getHotspotDegreeThreshold();
        List<Finding> findings = new ArrayList<>();
        for (Application app : graph.vertexSet()) {
            int inDegree = graph.inDegreeOf(app);
            if (inDegree > threshold) {
                findings.add(new Finding(FindingType.HUB, Severity.ERROR, List.of(app.id()),
                        "Application '" + app.id() + "' (" + app.name() + ") is a hub with in-degree "
                                + inDegree + " (threshold " + threshold
                                + ") — a potential single point of failure"));
            }
        }
        return findings;
    }
}
