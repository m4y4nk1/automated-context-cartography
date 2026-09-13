package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.List;

import org.jgrapht.Graph;
import org.springframework.stereotype.Component;

import com.vw.eacontext.graph.CycleDetectionService;
import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.insight.Detector;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.validation.Severity;

import lombok.RequiredArgsConstructor;

/** A directed cycle among applications in the Relationships graph. */
@Component
@RequiredArgsConstructor
public class CircularDependencyDetector implements Detector {

    private final CycleDetectionService cycleDetectionService;

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        List<Finding> findings = new ArrayList<>();
        for (List<String> cycle : cycleDetectionService.findCycles(graph)) {
            findings.add(new Finding(FindingType.CIRCULAR_DEPENDENCY, Severity.ERROR, cycle,
                    "Circular dependency detected: " + String.join(" -> ", cycle) + " -> " + cycle.get(0)));
        }
        return findings;
    }
}
