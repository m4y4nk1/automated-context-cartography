package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jgrapht.Graph;
import org.springframework.stereotype.Component;

import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.insight.Detector;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.validation.Severity;

/** An active interface whose provider application is end of life. */
@Component
public class LifecycleRiskEolProviderDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Map<String, Application> appById = DetectorSupport.applicationsById(model);
        List<Finding> findings = new ArrayList<>();
        for (Interface iface : model.interfaces()) {
            if (iface.interfaceStatus() != InterfaceStatus.ACTIVE) {
                continue;
            }
            Application provider = appById.get(iface.providerApplicationId());
            if (provider != null && provider.lifecycleStatus() != null && provider.lifecycleStatus().isEndOfLife()) {
                findings.add(new Finding(FindingType.LIFECYCLE_RISK_EOL_PROVIDER, Severity.WARNING,
                        DetectorSupport.ids(iface.id(), provider.id()),
                        "Active interface '" + iface.id() + "' (" + iface.name()
                                + ") is provided by end-of-life application '" + provider.id() + "'"));
            }
        }
        return findings;
    }
}
