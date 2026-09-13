package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.List;

import org.jgrapht.Graph;
import org.springframework.stereotype.Component;

import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.insight.Detector;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.validation.Severity;

/** A blank {@code OwnerEmployeeID} directly on the Applications record. */
@Component
public class MissingOwnerFieldDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        List<Finding> findings = new ArrayList<>();
        for (Application app : model.applications()) {
            if (DetectorSupport.isBlank(app.ownerEmployeeId())) {
                findings.add(new Finding(FindingType.MISSING_OWNER_FIELD, Severity.WARNING, List.of(app.id()),
                        "Application '" + app.id() + "' (" + app.name() + ") has a blank OwnerEmployeeID"));
            }
        }
        return findings;
    }
}
