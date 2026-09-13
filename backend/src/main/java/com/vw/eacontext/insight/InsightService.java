package com.vw.eacontext.insight;

import java.util.ArrayList;
import java.util.List;

import org.jgrapht.Graph;
import org.springframework.stereotype.Service;

import com.vw.eacontext.graph.GraphBuilderService;
import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs every registered {@link Detector} against the canonical model and its
 * graph, flattening their results into one {@link List} of {@link Finding}s.
 *
 * <p>This class intentionally contains no detection logic of its own — each
 * scenario in the anomaly catalogue is its own {@link Detector} bean,
 * autowired here by Spring. Adding a 19th scenario never requires touching
 * this class.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final GraphBuilderService graphBuilderService;
    private final List<Detector> detectors;

    /**
     * Runs all detectors against the given model (building the graph internally).
     *
     * @param model the canonical model to analyze (must not be {@code null})
     * @return all findings, in detector-registration order
     */
    public List<Finding> analyze(CanonicalModel model) {
        return analyze(model, graphBuilderService.build(model));
    }

    /**
     * Runs all detectors reusing a pre-built graph (avoids rebuilding).
     *
     * @param model the canonical model to analyze
     * @param graph the pre-built application/relationship graph
     * @return all findings, in detector-registration order
     */
    public List<Finding> analyze(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        List<Finding> findings = new ArrayList<>();
        for (Detector detector : detectors) {
            findings.addAll(detector.detect(model, graph));
        }
        log.info("Insight analysis produced {} finding(s) from {} detector(s)", findings.size(), detectors.size());
        return findings;
    }
}
