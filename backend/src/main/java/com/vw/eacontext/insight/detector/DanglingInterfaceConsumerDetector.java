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

/**
 * An {@link Interface} whose provider or consumer application is a ghost
 * reference — either end can be the broken one. One finding per record.
 */
@Component
public class DanglingInterfaceConsumerDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Set<String> applicationIds = DetectorSupport.applicationIds(model);
        List<Finding> findings = new ArrayList<>();
        for (Interface iface : model.interfaces()) {
            IngestionSupport.ReferenceCheck provider =
                    IngestionSupport.resolveReference(iface.providerApplicationId(), applicationIds);
            IngestionSupport.ReferenceCheck consumer =
                    IngestionSupport.resolveReference(iface.consumerApplicationId(), applicationIds);
            if (!provider.ghost() && !consumer.ghost()) {
                continue;
            }
            List<String> unknown = new ArrayList<>();
            if (provider.ghost()) {
                unknown.add("provider '" + provider.id() + "'");
            }
            if (consumer.ghost()) {
                unknown.add("consumer '" + consumer.id() + "'");
            }
            findings.add(new Finding(FindingType.DANGLING_INTERFACE_CONSUMER, Severity.ERROR,
                    DetectorSupport.ids(iface.id(),
                            provider.present() ? provider.id() : null, consumer.present() ? consumer.id() : null),
                    "Interface '" + iface.id() + "' (" + iface.name() + ") " + String.join(" and ", unknown)
                            + (unknown.size() > 1 ? " do not exist" : " does not exist")));
        }
        return findings;
    }
}
