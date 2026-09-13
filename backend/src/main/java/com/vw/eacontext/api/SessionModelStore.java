package com.vw.eacontext.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.springframework.stereotype.Component;

import com.vw.eacontext.dto.GraphStats;
import com.vw.eacontext.exception.ModelNotLoadedException;
import com.vw.eacontext.graph.GraphBuilderService;
import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.insight.GapComparison;
import com.vw.eacontext.insight.GapComparisonService;
import com.vw.eacontext.insight.InsightService;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory (no database) store for the current session's uploaded
 * {@link CanonicalModel} together with its derived artifacts — the JGraphT
 * {@link Graph}, insight {@link Finding}s, {@link GraphStats} and the
 * declared-vs-detected {@link GapComparison}.
 *
 * <p>Derived artifacts are computed once at {@link #load(CanonicalModel) load}
 * time and reused by subsequent read calls, so repeated {@code GET} requests do
 * not re-parse or recompute.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionModelStore {

    private final GraphBuilderService graphBuilderService;
    private final InsightService insightService;
    private final GapComparisonService gapComparisonService;

    private volatile Session session;

    /** Immutable snapshot of a loaded model and everything derived from it. */
    private record Session(
            CanonicalModel model,
            Graph<Application, RelationshipEdge> graph,
            List<Finding> findings,
            GraphStats stats,
            GapComparison gapComparison) {
    }

    /**
     * Loads a model and eagerly computes and caches its derived graph, findings,
     * statistics and gap comparison, replacing any previously loaded session.
     */
    public synchronized void load(CanonicalModel model) {
        Graph<Application, RelationshipEdge> graph = graphBuilderService.build(model);
        List<Finding> findings = insightService.analyze(model, graph);
        GraphStats stats = computeStats(model, graph, findings);
        GapComparison gapComparison = gapComparisonService.compare(model, findings);
        this.session = new Session(model, graph, findings, stats, gapComparison);
        log.info("Session loaded: {} applications, {} relationships, {} findings",
                model.applications().size(), model.relationships().size(), findings.size());
    }

    /** @return {@code true} if a model is currently loaded. */
    public boolean isLoaded() {
        return session != null;
    }

    public CanonicalModel getModel() {
        return require().model();
    }

    public Graph<Application, RelationshipEdge> getGraph() {
        return require().graph();
    }

    public List<Finding> getFindings() {
        return require().findings();
    }

    public GraphStats getStats() {
        return require().stats();
    }

    public GapComparison getGapComparison() {
        return require().gapComparison();
    }

    private Session require() {
        Session current = this.session;
        if (current == null) {
            throw new ModelNotLoadedException(
                    "No dataset loaded. Upload a file via POST /api/upload first.");
        }
        return current;
    }

    private GraphStats computeStats(CanonicalModel model, Graph<Application, RelationshipEdge> graph,
                                    List<Finding> findings) {
        String mostConnectedId = null;
        int maxInDegree = -1;
        for (Application app : graph.vertexSet()) {
            int inDegree = graph.inDegreeOf(app);
            if (inDegree > maxInDegree) {
                maxInDegree = inDegree;
                mostConnectedId = app.id();
            }
        }

        Set<String> domains = new HashSet<>();
        for (Application app : model.applications()) {
            if (app.businessDomain() != null && !app.businessDomain().isBlank()) {
                domains.add(app.businessDomain());
            }
        }

        long hubCount = findings.stream().filter(f -> f.type() == FindingType.HUB).count();
        long cycleCount = findings.stream().filter(f -> f.type() == FindingType.CIRCULAR_DEPENDENCY).count();
        long orphanCount = findings.stream().filter(f -> f.type() == FindingType.ORPHAN_APPLICATION).count();

        return GraphStats.builder()
                .applicationCount(model.applications().size())
                .relationshipCount(model.relationships().size())
                .interfaceCount(model.interfaces().size())
                .informationObjectCount(model.informationObjects().size())
                .businessProcessCount(model.businessProcesses().size())
                .domainCount(domains.size())
                .mostConnectedApplicationId(mostConnectedId)
                .maxDegree(Math.max(maxInDegree, 0))
                .cycleCount((int) cycleCount)
                .hubCount((int) hubCount)
                .orphanCount((int) orphanCount)
                .build();
    }
}
