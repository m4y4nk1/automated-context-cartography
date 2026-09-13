package com.vw.eacontext.ai;

import java.util.List;

import com.vw.eacontext.dto.GraphStats;
import com.vw.eacontext.insight.Finding;

/**
 * Produces a natural-language summary of an EA landscape from its detected
 * findings and graph statistics.
 *
 * <p>Implementations may be deterministic/template-based or backed by an LLM.
 * The template implementation is the default so the application is fully
 * functional before any LLM access is configured.</p>
 */
public interface SummaryGenerator {

    /**
     * Generates a summary.
     *
     * @param findings the insight findings (may be empty, not {@code null})
     * @param stats    high-level landscape statistics
     * @return a human-readable summary
     */
    String summarize(List<Finding> findings, GraphStats stats);
}

