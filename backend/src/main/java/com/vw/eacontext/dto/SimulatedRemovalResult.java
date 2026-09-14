package com.vw.eacontext.dto;

import java.util.List;
import java.util.Set;

import com.vw.eacontext.insight.Finding;

/**
 * The projected effect of retiring one application: which applications would
 * be directly affected (the existing blast radius), which problems would
 * newly appear (e.g. relationships/interfaces left pointing at the retired
 * app), and which existing problems would disappear (e.g. a hub or
 * ownership-gap finding about the retired app itself).
 *
 * @param removedApplicationId the application the simulation was run for
 * @param upstream              ids of applications that (transitively) depend on it — the ones
 *                              most directly impacted by its removal
 * @param downstream            ids of applications it (transitively) depends on
 * @param newFindings           findings present only after the removal (new problems it would create)
 * @param resolvedFindings      findings present only before the removal (problems that would disappear,
 *                              including any finding about the removed application itself)
 */
public record SimulatedRemovalResult(
        String removedApplicationId,
        Set<String> upstream,
        Set<String> downstream,
        List<Finding> newFindings,
        List<Finding> resolvedFindings) {

    public SimulatedRemovalResult {
        upstream = upstream == null ? Set.of() : Set.copyOf(upstream);
        downstream = downstream == null ? Set.of() : Set.copyOf(downstream);
        newFindings = newFindings == null ? List.of() : List.copyOf(newFindings);
        resolvedFindings = resolvedFindings == null ? List.of() : List.copyOf(resolvedFindings);
    }
}
