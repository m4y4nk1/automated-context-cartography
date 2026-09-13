package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jgrapht.Graph;
import org.springframework.stereotype.Component;

import com.vw.eacontext.config.InsightProperties;
import com.vw.eacontext.graph.RelationshipEdge;
import com.vw.eacontext.insight.Detector;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.validation.Severity;

import lombok.RequiredArgsConstructor;

/** Two or more applications sharing the same name. */
@Component
@RequiredArgsConstructor
public class DuplicateApplicationDetector implements Detector {

    private final InsightProperties properties;

    @Override
    public List<Finding> detect(CanonicalModel model, Graph<Application, RelationshipEdge> graph) {
        boolean caseSensitive = properties.isDuplicateNameCaseSensitive();
        Map<String, List<Application>> byName = new LinkedHashMap<>();
        for (Application app : model.applications()) {
            if (DetectorSupport.isBlank(app.name())) {
                continue;
            }
            // Collapse incidental whitespace differences (leading/trailing, doubled
            // internal spaces) before grouping, so "CRM Suite" and "CRM  Suite " are
            // still recognized as the same name.
            String normalized = app.name().trim().replaceAll("\\s+", " ");
            String key = caseSensitive ? normalized : normalized.toLowerCase(Locale.ROOT);
            byName.computeIfAbsent(key, k -> new ArrayList<>()).add(app);
        }

        List<Finding> findings = new ArrayList<>();
        byName.forEach((key, apps) -> {
            if (apps.size() > 1) {
                List<String> ids = apps.stream().map(Application::id).toList();
                findings.add(new Finding(FindingType.DUPLICATE_APPLICATION, Severity.WARNING, ids,
                        apps.size() + " applications share the name '" + apps.get(0).name() + "': " + ids));
            }
        });
        return findings;
    }
}
