package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.HashMap;
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
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.validation.Severity;

/** An information flow whose interface has been deprecated but is still in use. */
@Component
public class DeprecatedInterfaceInUseDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Map<String, Interface> interfaceById = new HashMap<>();
        for (Interface iface : model.interfaces()) {
            if (!DetectorSupport.isBlank(iface.id())) {
                interfaceById.put(iface.id(), iface);
            }
        }

        List<Finding> findings = new ArrayList<>();
        for (InformationObject info : model.informationObjects()) {
            Interface iface = interfaceById.get(info.interfaceId());
            if (iface != null && iface.interfaceStatus() == InterfaceStatus.DEPRECATED) {
                findings.add(new Finding(FindingType.DEPRECATED_INTERFACE_IN_USE, Severity.ERROR,
                        DetectorSupport.ids(info.id(), iface.id()),
                        "Information flow '" + info.id() + "' (" + info.informationObject()
                                + ") travels over deprecated interface '" + iface.id() + "'"));
            }
        }
        return findings;
    }
}
