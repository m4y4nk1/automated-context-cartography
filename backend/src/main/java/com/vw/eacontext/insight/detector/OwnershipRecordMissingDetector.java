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
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.validation.Severity;

/** An application with no {@link ApplicationOwnership} record at all. */
@Component
public class OwnershipRecordMissingDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Set<String> ownedApplicationIds = new HashSet<>();
        for (ApplicationOwnership ownership : model.applicationOwnerships()) {
            if (!DetectorSupport.isBlank(ownership.applicationId())) {
                ownedApplicationIds.add(ownership.applicationId());
            }
        }

        List<Finding> findings = new ArrayList<>();
        for (Application app : model.applications()) {
            if (!DetectorSupport.isBlank(app.id()) && !ownedApplicationIds.contains(app.id())) {
                findings.add(new Finding(FindingType.OWNERSHIP_RECORD_MISSING, Severity.WARNING, List.of(app.id()),
                        "Application '" + app.id() + "' (" + app.name() + ") has no ownership record"));
            }
        }
        return findings;
    }
}
