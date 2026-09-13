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
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.validation.Severity;

/** A {@link ProcessMapping} whose supporting application is a ghost reference. */
@Component
public class UnmappedProcessApplicationDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Set<String> applicationIds = DetectorSupport.applicationIds(model);
        List<Finding> findings = new ArrayList<>();
        for (ProcessMapping mapping : model.processMappings()) {
            IngestionSupport.ReferenceCheck check =
                    IngestionSupport.resolveReference(mapping.supportingApplicationId(), applicationIds);
            if (check.ghost()) {
                findings.add(new Finding(FindingType.UNMAPPED_PROCESS_APPLICATION, Severity.ERROR,
                        DetectorSupport.ids(mapping.id(), mapping.businessProcessId()),
                        "Process mapping '" + mapping.id() + "' references unknown supporting application '"
                                + check.id() + "'"));
            }
        }
        return findings;
    }
}
