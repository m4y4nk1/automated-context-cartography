package com.vw.eacontext.insight;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.springframework.stereotype.Service;

import com.vw.eacontext.dto.SimulatedRemovalResult;
import com.vw.eacontext.graph.GraphBuilderService;
import com.vw.eacontext.graph.ImpactAnalysisService;
import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

import lombok.RequiredArgsConstructor;

/**
 * "What if we retired this application?" — projects the effect of removing
 * one application by re-running the existing detector suite against a copy
 * of the model with that application (and only that application) taken out.
 *
 * <p>Every other row (relationships, interfaces, information flows, process
 * mappings, ownership records) is left exactly as-is. This is deliberate: a
 * relationship that used to point at the removed application now points at a
 * ghost id, which the existing ghost-reference detectors
 * ({@code BrokenRelationshipReferenceDetector}, {@code DanglingInterfaceConsumerDetector},
 * {@code BrokenInformationFlowReferenceDetector}, {@code UnmappedProcessApplicationDetector})
 * already know how to catch — exactly the same mechanism that already
 * detects the dataset's own seeded ghost references. No new detection logic
 * is needed; only the diff between the before/after finding sets is new.</p>
 */
@Service
@RequiredArgsConstructor
public class ChangeSimulationService {

    private final GraphBuilderService graphBuilderService;
    private final InsightService insightService;
    private final ImpactAnalysisService impactAnalysisService;

    /**
     * @param model            the current canonical model
     * @param graph            the current model's pre-built graph (used for the blast-radius preview)
     * @param baselineFindings the current model's findings (already computed, reused rather than recomputed)
     * @param appId            the application being considered for removal
     * @return the projected new/resolved findings and the blast-radius preview
     * @throws com.vw.eacontext.exception.EaNotFoundException if no application with {@code appId} exists
     */
    public SimulatedRemovalResult simulateRemoval(CanonicalModel model, Graph<Application, RelationshipEdge> graph,
                                                  List<Finding> baselineFindings, String appId) {
        // Reuses the existing lookup/blast-radius computation as-is — also gives the
        // "not found" (404) behavior other node-scoped endpoints already have, for free.
        var impact = impactAnalysisService.impactAnalysis(graph, appId);

        CanonicalModel withoutApp = model.toBuilder()
                .applications(model.applications().stream()
                        .filter(app -> !appId.equals(app.id()))
                        .toList())
                .build();
        Graph<Application, RelationshipEdge> graphWithoutApp = graphBuilderService.build(withoutApp);
        List<Finding> simulatedFindings = insightService.analyze(withoutApp, graphWithoutApp);

        Set<String> baselineKeys = keysOf(baselineFindings);
        Set<String> simulatedKeys = keysOf(simulatedFindings);

        List<Finding> newFindings = simulatedFindings.stream()
                .filter(finding -> !baselineKeys.contains(key(finding)))
                .toList();
        List<Finding> resolvedFindings = baselineFindings.stream()
                .filter(finding -> !simulatedKeys.contains(key(finding)))
                .toList();

        return new SimulatedRemovalResult(appId, impact.upstream(), impact.downstream(), newFindings, resolvedFindings);
    }

    private static Set<String> keysOf(List<Finding> findings) {
        Set<String> keys = new HashSet<>();
        for (Finding finding : findings) {
            keys.add(key(finding));
        }
        return keys;
    }

    /** Identifies "the same finding" across the before/after sets: same type, same related ids. */
    private static String key(Finding finding) {
        return finding.type() + "|" + finding.relatedEntityIds().stream().sorted()
                .collect(Collectors.joining(","));
    }
}
