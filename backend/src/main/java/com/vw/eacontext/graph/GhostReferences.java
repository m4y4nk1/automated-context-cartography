package com.vw.eacontext.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The set of application ids that records refer to but which have no row in
 * {@code Applications} — "ghost references".
 *
 * <p>These are a <em>projection-level</em> concept only. They deliberately never
 * enter the {@link com.vw.eacontext.model.CanonicalModel} or the analytical
 * graph, so no detector can mistake a placeholder for a real application; the
 * broken references are already reported as findings by the broken-reference
 * detectors. Materialising them here simply means the diagram shows the dangling
 * edge instead of silently dropping it.</p>
 *
 * @param referencedBy ghost application id &rarr; the ids of the records that
 *                     point at it (e.g. {@code REL-0065}, {@code BPM-0038})
 */
public record GhostReferences(Map<String, Set<String>> referencedBy) {

    public GhostReferences {
        referencedBy = referencedBy == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(referencedBy));
    }

    /** An empty result — the common case for a dataset with clean references. */
    public static GhostReferences none() {
        return new GhostReferences(Map.of());
    }

    /** @return every unresolved application id, in first-seen order. */
    public Set<String> ids() {
        return referencedBy.keySet();
    }

    public boolean isEmpty() {
        return referencedBy.isEmpty();
    }

    /**
     * @param id an application id
     * @return {@code true} if the id is referenced somewhere but has no
     *         application row of its own
     */
    public boolean isGhost(String id) {
        return id != null && referencedBy.containsKey(id);
    }

    /** @return the record ids pointing at {@code id}, or an empty set. */
    public Set<String> sources(String id) {
        return referencedBy.getOrDefault(id, Set.of());
    }
}
