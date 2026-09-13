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
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.validation.Severity;

/** An {@link ApplicationOwnership} record that exists but has a blank business owner. */
@Component
public class OwnershipPartialGapDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        List<Finding> findings = new ArrayList<>();
        for (ApplicationOwnership ownership : model.applicationOwnerships()) {
            if (DetectorSupport.isBlank(ownership.businessOwner())) {
                findings.add(new Finding(FindingType.OWNERSHIP_PARTIAL_GAP, Severity.INFO,
                        DetectorSupport.ids(ownership.id(), ownership.applicationId()),
                        "Ownership record '" + ownership.id() + "' for application '"
                                + ownership.applicationId() + "' has no business owner"));
            }
        }
        return findings;
    }
}
