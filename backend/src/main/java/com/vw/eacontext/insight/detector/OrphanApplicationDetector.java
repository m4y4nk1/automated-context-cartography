package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.springframework.stereotype.Component;

import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.insight.Detector;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.validation.Severity;

/** An application referenced by no relationship, interface, information flow, or process mapping. */
@Component
public class OrphanApplicationDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Set<String> referenced = new HashSet<>();
        for (Relationship relationship : model.relationships()) {
            addIfPresent(referenced, relationship.sourceApplicationId());
            addIfPresent(referenced, relationship.targetApplicationId());
        }
        for (Interface iface : model.interfaces()) {
            addIfPresent(referenced, iface.providerApplicationId());
            addIfPresent(referenced, iface.consumerApplicationId());
        }
        for (InformationObject info : model.informationObjects()) {
            addIfPresent(referenced, info.sourceApplicationId());
            addIfPresent(referenced, info.targetApplicationId());
        }
        for (ProcessMapping mapping : model.processMappings()) {
            addIfPresent(referenced, mapping.supportingApplicationId());
        }

        List<Finding> findings = new ArrayList<>();
        for (Application app : model.applications()) {
            if (!DetectorSupport.isBlank(app.id()) && !referenced.contains(app.id())) {
                findings.add(new Finding(FindingType.ORPHAN_APPLICATION, Severity.WARNING, List.of(app.id()),
                        "Application '" + app.id() + "' (" + app.name()
                                + ") is not referenced by any relationship, interface, information flow, "
                                + "or process mapping"));
            }
        }
        return findings;
    }

    private void addIfPresent(Set<String> ids, String id) {
        if (!DetectorSupport.isBlank(id)) {
            ids.add(id);
        }
    }
}
