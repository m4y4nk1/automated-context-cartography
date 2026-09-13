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
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.validation.Severity;

/** An {@link Interface} whose consumer application is a ghost reference. */
@Component
public class DanglingInterfaceConsumerDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Set<String> applicationIds = DetectorSupport.applicationIds(model);
        List<Finding> findings = new ArrayList<>();
        for (Interface iface : model.interfaces()) {
            IngestionSupport.ReferenceCheck check =
                    IngestionSupport.resolveReference(iface.consumerApplicationId(), applicationIds);
            if (check.ghost()) {
                findings.add(new Finding(FindingType.DANGLING_INTERFACE_CONSUMER, Severity.ERROR,
                        DetectorSupport.ids(iface.id(), iface.providerApplicationId()),
                        "Interface '" + iface.id() + "' (" + iface.name() + ") consumer '"
                                + check.id() + "' does not exist"));
            }
        }
        return findings;
    }
}
