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

/**
 * An interface whose provider application is end of life.
 *
 * <p>Not limited to {@link InterfaceStatus#ACTIVE}: a deprecated interface is
 * still a live integration until it is actually removed, and an end-of-life
 * provider behind it is if anything more urgent. The Auriga dataset's own
 * seeded case (an EoL General Ledger providing an interface) is deprecated, so
 * an active-only check misses it outright.</p>
 */
@Component
public class LifecycleRiskEolProviderDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Map<String, Application> appById = DetectorSupport.applicationsById(model);
        List<Finding> findings = new ArrayList<>();
        for (Interface iface : model.interfaces()) {
            Application provider = appById.get(iface.providerApplicationId());
            if (provider != null && provider.lifecycleStatus() != null && provider.lifecycleStatus().isEndOfLife()) {
                String subject = iface.interfaceStatus() == InterfaceStatus.DEPRECATED
                        ? "Deprecated interface '" : "Interface '";
                findings.add(new Finding(FindingType.LIFECYCLE_RISK_EOL_PROVIDER, Severity.WARNING,
                        DetectorSupport.ids(iface.id(), provider.id()),
                        subject + iface.id() + "' (" + iface.name()
                                + ") is provided by end-of-life application '" + provider.id() + "'"));
            }
        }
        return findings;
    }
}
