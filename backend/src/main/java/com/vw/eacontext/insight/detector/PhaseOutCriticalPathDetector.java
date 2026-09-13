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
import com.vw.eacontext.model.BusinessCriticality;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.LifecycleStatus;
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.validation.Severity;

/** A phase-out application still supporting a mission-critical business process. */
@Component
public class PhaseOutCriticalPathDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Map<String, Application> appById = DetectorSupport.applicationsById(model);
        List<Finding> findings = new ArrayList<>();
        for (ProcessMapping mapping : model.processMappings()) {
            Application app = appById.get(mapping.supportingApplicationId());
            if (app == null || app.lifecycleStatus() != LifecycleStatus.PHASE_OUT) {
                continue;
            }
            if (mapping.processCriticality() == BusinessCriticality.MISSION_CRITICAL) {
                findings.add(new Finding(FindingType.PHASE_OUT_CRITICAL_PATH, Severity.WARNING,
                        DetectorSupport.ids(app.id(), mapping.businessProcessId()),
                        "Phase-out application '" + app.id() + "' (" + app.name()
                                + ") still supports mission-critical process '" + mapping.businessProcessId() + "'"));
            }
        }
        return findings;
    }
}
