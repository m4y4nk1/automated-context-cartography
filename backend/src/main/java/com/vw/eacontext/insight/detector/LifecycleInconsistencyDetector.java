package com.vw.eacontext.insight.detector;

import java.time.LocalDate;
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
import com.vw.eacontext.model.LifecycleStatus;
import com.vw.eacontext.validation.Severity;

import lombok.RequiredArgsConstructor;

/**
 * An application marked {@code Active} whose lifecycle end date has already
 * passed (or, with a configured lookahead, is imminent).
 */
@Component
@RequiredArgsConstructor
public class LifecycleInconsistencyDetector implements Detector {

    private final InsightProperties properties;

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        LocalDate cutoff = LocalDate.now().plusDays(Math.max(0, properties.getLifecycleRiskLookaheadDays()));
        List<Finding> findings = new ArrayList<>();
        for (Application app : model.applications()) {
            if (app.lifecycleStatus() == LifecycleStatus.ACTIVE && app.lifecycleEndDate() != null
                    && app.lifecycleEndDate().isBefore(cutoff)) {
                findings.add(new Finding(FindingType.LIFECYCLE_INCONSISTENCY, Severity.WARNING, List.of(app.id()),
                        "Application '" + app.id() + "' (" + app.name() + ") is Active but its lifecycle end date "
                                + app.lifecycleEndDate() + " has already passed"));
            }
        }
        return findings;
    }
}
