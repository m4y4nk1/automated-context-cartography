package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.springframework.stereotype.Component;

import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.ingestion.IngestionSupport;
import com.vw.eacontext.insight.Detector;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.validation.Severity;

/** An {@link InformationObject} flow whose source or target application is a ghost reference. */
@Component
public class BrokenInformationFlowReferenceDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Set<String> applicationIds = DetectorSupport.applicationIds(model);
        List<Finding> findings = new ArrayList<>();
        for (InformationObject info : model.informationObjects()) {
            IngestionSupport.ReferenceCheck source =
                    IngestionSupport.resolveReference(info.sourceApplicationId(), applicationIds);
            if (source.ghost()) {
                findings.add(new Finding(FindingType.BROKEN_INFORMATION_FLOW_REFERENCE, Severity.ERROR,
                        DetectorSupport.ids(info.id()),
                        "Information flow '" + info.id() + "' (" + info.informationObject()
                                + ") source '" + source.id() + "' does not exist"));
            }
            IngestionSupport.ReferenceCheck target =
                    IngestionSupport.resolveReference(info.targetApplicationId(), applicationIds);
            if (target.ghost()) {
                findings.add(new Finding(FindingType.BROKEN_INFORMATION_FLOW_REFERENCE, Severity.ERROR,
                        DetectorSupport.ids(info.id()),
                        "Information flow '" + info.id() + "' (" + info.informationObject()
                                + ") target '" + target.id() + "' does not exist"));
            }
        }
        return findings;
    }
}
