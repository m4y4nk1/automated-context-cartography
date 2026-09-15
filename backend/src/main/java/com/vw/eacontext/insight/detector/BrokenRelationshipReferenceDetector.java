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
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.validation.Severity;

/**
 * A {@link Relationship} whose source or target application is a ghost
 * reference — either end can be the broken one. One finding per record.
 */
@Component
public class BrokenRelationshipReferenceDetector implements Detector {

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        Set<String> applicationIds = DetectorSupport.applicationIds(model);
        List<Finding> findings = new ArrayList<>();
        for (Relationship relationship : model.relationships()) {
            IngestionSupport.ReferenceCheck source =
                    IngestionSupport.resolveReference(relationship.sourceApplicationId(), applicationIds);
            IngestionSupport.ReferenceCheck target =
                    IngestionSupport.resolveReference(relationship.targetApplicationId(), applicationIds);
            if (!source.ghost() && !target.ghost()) {
                continue;
            }
            List<String> unknown = new ArrayList<>();
            if (source.ghost()) {
                unknown.add("source application '" + source.id() + "'");
            }
            if (target.ghost()) {
                unknown.add("target application '" + target.id() + "'");
            }
            findings.add(new Finding(FindingType.BROKEN_RELATIONSHIP_REFERENCE, Severity.ERROR,
                    DetectorSupport.ids(relationship.id(),
                            source.present() ? source.id() : null, target.present() ? target.id() : null),
                    "Relationship '" + relationship.id() + "' references unknown " + String.join(" and ", unknown)));
        }
        return findings;
    }
}
