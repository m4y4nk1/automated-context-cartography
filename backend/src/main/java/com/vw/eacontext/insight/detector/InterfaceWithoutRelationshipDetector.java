package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.springframework.stereotype.Component;

import com.vw.eacontext.graph.ApplicationPairKey;
import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.insight.Detector;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.validation.Severity;

/** An interface provider/consumer pair with no corresponding row in Relationships. */
@Component
public class InterfaceWithoutRelationshipDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Set<String> relationshipPairs = new HashSet<>();
        for (Relationship relationship : model.relationships()) {
            if (!DetectorSupport.isBlank(relationship.sourceApplicationId())
                    && !DetectorSupport.isBlank(relationship.targetApplicationId())) {
                relationshipPairs.add(ApplicationPairKey.of(
                        relationship.sourceApplicationId(), relationship.targetApplicationId()));
            }
        }

        List<Finding> findings = new ArrayList<>();
        for (Interface iface : model.interfaces()) {
            if (DetectorSupport.isBlank(iface.providerApplicationId())
                    || DetectorSupport.isBlank(iface.consumerApplicationId())) {
                continue;
            }
            // A Relationship recorded in either direction between these two
            // applications still counts as "declared" — Relationships and
            // Interfaces are captured independently in the source data and
            // aren't guaranteed to agree on which side is the dependent one.
            String pair = ApplicationPairKey.of(iface.providerApplicationId(), iface.consumerApplicationId());
            if (!relationshipPairs.contains(pair)) {
                findings.add(new Finding(FindingType.INTERFACE_WITHOUT_RELATIONSHIP, Severity.WARNING,
                        DetectorSupport.ids(iface.id(), iface.providerApplicationId(), iface.consumerApplicationId()),
                        "Interface '" + iface.id() + "' (" + iface.name() + ") between '"
                                + iface.providerApplicationId() + "' and '" + iface.consumerApplicationId()
                                + "' has no corresponding relationship"));
            }
        }
        return findings;
    }
}
