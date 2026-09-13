package com.vw.eacontext.ai;

import java.util.List;
import java.util.Set;

import com.vw.eacontext.dto.GraphStats;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.validation.Severity;

/**
 * A deterministic, template-based {@link SummaryGenerator} that requires no
 * external AI service. It composes a stable, human-readable summary from the
 * findings and graph statistics, guaranteeing the application works before any
 * LLM is wired in.
 *
 * <p>Output is fully deterministic: entity ids within each section are sorted,
 * so the same input always yields the same summary.</p>
 */
public class TemplateSummaryGenerator implements SummaryGenerator {

    @Override
    public String summarize(List<Finding> findings, GraphStats stats) {
        List<Finding> safeFindings = findings == null ? List.of() : findings;

        StringBuilder sb = new StringBuilder();
        appendOverview(sb, stats);
        appendIssueTotals(sb, safeFindings);
        appendSection(sb, safeFindings, "Hubs (single points of failure)", "application(s)",
                FindingType.HUB);
        appendSection(sb, safeFindings, "Circular dependencies", "application(s) in a cycle",
                FindingType.CIRCULAR_DEPENDENCY);
        appendSection(sb, safeFindings, "Broken references", "record(s)",
                FindingType.BROKEN_RELATIONSHIP_REFERENCE, FindingType.DANGLING_INTERFACE_CONSUMER,
                FindingType.BROKEN_INFORMATION_FLOW_REFERENCE, FindingType.UNMAPPED_PROCESS_APPLICATION);
        appendSection(sb, safeFindings, "Duplicate applications", "application(s)",
                FindingType.DUPLICATE_APPLICATION);
        appendSection(sb, safeFindings, "Orphan applications", "application(s)",
                FindingType.ORPHAN_APPLICATION);
        appendSection(sb, safeFindings, "Ownership gaps", "record(s)",
                FindingType.OWNERSHIP_RECORD_MISSING, FindingType.OWNERSHIP_PARTIAL_GAP,
                FindingType.MISSING_OWNER_FIELD);
        appendSection(sb, safeFindings, "Lifecycle risks", "record(s)",
                FindingType.LIFECYCLE_RISK_CRITICAL_PROCESS, FindingType.LIFECYCLE_RISK_EOL_PROVIDER,
                FindingType.LIFECYCLE_INCONSISTENCY, FindingType.PHASE_OUT_CRITICAL_PATH);
        appendSection(sb, safeFindings, "Deprecated interfaces still in use", "flow(s)",
                FindingType.DEPRECATED_INTERFACE_IN_USE);
        appendSection(sb, safeFindings, "Interfaces without a relationship", "interface(s)",
                FindingType.INTERFACE_WITHOUT_RELATIONSHIP);
        appendSection(sb, safeFindings, "Sensitive data over insecure flows", "flow(s)",
                FindingType.SENSITIVE_DATA_INSECURE_FLOW);
        appendHotspot(sb, stats);
        return sb.toString().stripTrailing();
    }

    private void appendOverview(StringBuilder sb, GraphStats stats) {
        if (stats == null) {
            sb.append("Analyzed the EA landscape.\n");
            return;
        }
        sb.append(String.format(
                "Analyzed %d application(s) across %d domain(s) and %d business process(es), "
                        + "connected by %d relationship(s) and %d interface(s); %d information flow(s) tracked.%n",
                stats.applicationCount(), stats.domainCount(), stats.businessProcessCount(),
                stats.relationshipCount(), stats.interfaceCount(), stats.informationObjectCount()));
    }

    private void appendIssueTotals(StringBuilder sb, List<Finding> findings) {
        if (findings.isEmpty()) {
            sb.append("No issues were detected.\n");
            return;
        }
        long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        long infos = findings.stream().filter(f -> f.severity() == Severity.INFO).count();
        sb.append(String.format("Detected %d issue(s): %d error(s), %d warning(s), %d informational.%n",
                findings.size(), errors, warnings, infos));
    }

    private void appendSection(StringBuilder sb, List<Finding> findings, String title, String noun,
                               FindingType... types) {
        Set<FindingType> typeSet = Set.of(types);
        List<Finding> matching = findings.stream().filter(f -> typeSet.contains(f.type())).toList();
        if (matching.isEmpty()) {
            return;
        }
        List<String> ids = matching.stream()
                .flatMap(f -> f.relatedEntityIds().stream())
                .distinct()
                .sorted()
                .toList();
        sb.append(String.format("- %s: %d finding(s) touching %s (%s).%n",
                title, matching.size(), noun, String.join(", ", ids)));
    }

    private void appendHotspot(StringBuilder sb, GraphStats stats) {
        if (stats == null) {
            return;
        }
        if (stats.mostConnectedApplicationId() != null) {
            sb.append(String.format("Most connected application: %s (in-degree %d).%n",
                    stats.mostConnectedApplicationId(), stats.maxDegree()));
        }
        if (stats.hubCount() > 0) {
            sb.append(String.format("%d hub application(s) exceed the dependency threshold.%n", stats.hubCount()));
        }
        if (stats.cycleCount() > 0) {
            sb.append(String.format("%d circular dependency chain(s) detected.%n", stats.cycleCount()));
        }
        if (stats.orphanCount() > 0) {
            sb.append(String.format("%d application(s) are orphaned (not referenced anywhere).%n",
                    stats.orphanCount()));
        }
    }
}
