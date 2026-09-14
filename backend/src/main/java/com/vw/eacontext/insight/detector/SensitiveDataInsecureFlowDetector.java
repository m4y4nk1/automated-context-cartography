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
import com.vw.eacontext.model.Classification;
import com.vw.eacontext.model.Hosting;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.model.Protocol;
import com.vw.eacontext.validation.Severity;

/**
 * Confidential/PII or restricted/PCI data flowing over a file-transfer and/or
 * deprecated interface.
 *
 * <p>The trigger stays exactly classification + interface protocol/status —
 * broadening it to require the target application be resolved and public-facing
 * would risk a false negative on a dataset whose seeded scenario doesn't
 * literally use {@link Hosting#PUBLIC_CLOUD}. Instead, the target application
 * (when it resolves) is named in the finding as extra context: an architect
 * reading "lands in a public-facing application" knows exactly why this flow is
 * more urgent than one landing on an internal system.</p>
 */
@Component
public class SensitiveDataInsecureFlowDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Map<String, Interface> interfaceById = new HashMap<>();
        for (Interface iface : model.interfaces()) {
            if (!DetectorSupport.isBlank(iface.id())) {
                interfaceById.put(iface.id(), iface);
            }
        }
        Map<String, Application> appById = DetectorSupport.applicationsById(model);

        List<Finding> findings = new ArrayList<>();
        for (InformationObject info : model.informationObjects()) {
            if (info.classification() != Classification.CONFIDENTIAL_PII
                    && info.classification() != Classification.RESTRICTED_PCI) {
                continue;
            }
            Interface iface = interfaceById.get(info.interfaceId());
            if (iface == null) {
                continue;
            }
            boolean insecureProtocol = iface.protocol() == Protocol.SFTP_FILE;
            boolean deprecated = iface.interfaceStatus() == InterfaceStatus.DEPRECATED;
            if (insecureProtocol || deprecated) {
                // May be null: a ghost target, or no target at all. Either way the
                // flow is still flagged — only the "public-facing" detail is lost.
                Application target = appById.get(info.targetApplicationId());
                boolean publicFacing = target != null && target.hosting() == Hosting.PUBLIC_CLOUD;
                findings.add(new Finding(FindingType.SENSITIVE_DATA_INSECURE_FLOW, Severity.ERROR,
                        DetectorSupport.ids(info.id(), iface.id(), target == null ? null : target.id()),
                        "Sensitive (" + info.classification() + ") information flow '" + info.id() + "' ("
                                + info.informationObject() + ") travels over interface '" + iface.id() + "' which is "
                                + describe(insecureProtocol, deprecated)
                                + landingClause(target, publicFacing)));
            }
        }
        return findings;
    }

    private String describe(boolean insecureProtocol, boolean deprecated) {
        if (insecureProtocol && deprecated) {
            return "an SFTP/file transfer and deprecated";
        }
        return insecureProtocol ? "an SFTP/file transfer" : "deprecated";
    }

    private String landingClause(Application target, boolean publicFacing) {
        if (target == null) {
            return "";
        }
        return publicFacing
                ? ", landing in a public-facing application ('" + target.id() + "', " + target.hosting() + ")"
                : ", landing in '" + target.id() + "'";
    }
}
