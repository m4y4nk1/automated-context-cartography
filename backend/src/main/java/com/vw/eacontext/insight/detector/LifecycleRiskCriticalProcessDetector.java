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
import com.vw.eacontext.model.ProcessCriticality;
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.validation.Severity;

/** An end-of-life application that supports a mission-critical business process. */
@Component
public class LifecycleRiskCriticalProcessDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Map<String, Application> appById = DetectorSupport.applicationsById(model);
        List<Finding> findings = new ArrayList<>();
        for (ProcessMapping mapping : model.processMappings()) {
            Application app = appById.get(mapping.supportingApplicationId());
            if (app == null || app.lifecycleStatus() == null || !app.lifecycleStatus().isEndOfLife()) {
                continue;
            }
            if (mapping.processCriticality() == ProcessCriticality.MISSION_CRITICAL) {
                findings.add(new Finding(FindingType.LIFECYCLE_RISK_CRITICAL_PROCESS, Severity.ERROR,
                        DetectorSupport.ids(app.id(), mapping.businessProcessId()),
                        "End-of-life application '" + app.id() + "' (" + app.name()
                                + ") supports mission-critical process '" + mapping.businessProcessId() + "'"));
            }
        }
        return findings;
    }
}
