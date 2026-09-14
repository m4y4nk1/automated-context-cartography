package com.vw.eacontext.graph;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.model.Relationship;

import lombok.extern.slf4j.Slf4j;

/**
 * Finds application ids that are referenced by a relationship, interface,
 * information flow, process mapping or ownership record but have no row in
 * {@code Applications}.
 *
 * <p>Nothing here is dataset-specific: the result is derived by diffing the
 * referenced ids against {@link CanonicalModel#applications()}, so a dataset
 * with clean referential integrity yields {@link GhostReferences#none()} and
 * every projection behaves exactly as it did before ghosts were rendered.</p>
 *
 * <p>Both endpoints of every record are checked, not just the target side — the
 * bundled sample happens to only break on targets, but a dataset may just as
 * easily reference a missing source application.</p>
 */
@Slf4j
@Service
public class GhostReferenceResolver {

    /**
     * @param model the canonical model (must not be {@code null})
     * @return the unresolved application ids, each mapped to the records that
     *         reference it
     */
    public GhostReferences resolve(CanonicalModel model) {
        Set<String> known = new HashSet<>();
        for (Application app : model.applications()) {
            if (!isBlank(app.id())) {
                known.add(app.id());
            }
        }

        Map<String, Set<String>> referencedBy = new LinkedHashMap<>();
        for (Relationship relationship : model.relationships()) {
            record(referencedBy, known, relationship.sourceApplicationId(), relationship.id());
            record(referencedBy, known, relationship.targetApplicationId(), relationship.id());
        }
        for (Interface iface : model.interfaces()) {
            record(referencedBy, known, iface.providerApplicationId(), iface.id());
            record(referencedBy, known, iface.consumerApplicationId(), iface.id());
        }
        for (InformationObject info : model.informationObjects()) {
            record(referencedBy, known, info.sourceApplicationId(), info.id());
            record(referencedBy, known, info.targetApplicationId(), info.id());
        }
        for (ProcessMapping mapping : model.processMappings()) {
            record(referencedBy, known, mapping.supportingApplicationId(), mapping.id());
        }
        for (ApplicationOwnership ownership : model.applicationOwnerships()) {
            record(referencedBy, known, ownership.applicationId(), ownership.id());
        }

        if (!referencedBy.isEmpty()) {
            log.info("Resolved {} unresolved application reference(s): {}",
                    referencedBy.size(), referencedBy.keySet());
        }
        return new GhostReferences(referencedBy);
    }

    private static void record(Map<String, Set<String>> referencedBy, Set<String> known,
                               String referencedId, String recordId) {
        if (isBlank(referencedId) || known.contains(referencedId)) {
            return;
        }
        referencedBy.computeIfAbsent(referencedId, id -> new LinkedHashSet<>()).add(recordId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
