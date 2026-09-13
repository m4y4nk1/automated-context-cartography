package com.vw.eacontext.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.vw.eacontext.ingestion.IngestionSupport;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.BusinessProcess;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.DataQualityGap;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.model.Relationship;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates a parsed {@link CanonicalModel} for data-quality issues.
 *
 * <p>All findings are collected into a {@link ValidationReport}; this service
 * never throws on data issues. It checks:</p>
 * <ul>
 *   <li><b>Required fields</b> — every entity must have a non-blank {@code id} (and
 *       a name where one applies); ids must be unique within each entity type (ERROR).</li>
 *   <li><b>Completeness</b> — fields the dataset report marks as required
 *       (e.g. an application's business domain/criticality/lifecycle status) (WARNING).</li>
 *   <li><b>Referential integrity</b> — cross-sheet foreign keys must resolve to
 *       an existing record; a ghost id is reported, never thrown on
 *       (ERROR for broken application references, WARNING for a dangling
 *       information-flow-to-interface link).</li>
 * </ul>
 *
 * <p>This service intentionally covers only required-field and referential
 * checks; architectural-pattern detection (ownership gaps, orphans, hubs,
 * lifecycle risk, etc.) lives exclusively in {@code insight/} so the two
 * layers don't duplicate the same logic.</p>
 */
@Slf4j
@Service
public class ValidationService {

    /**
     * Validates the given model and returns a report of all issues found.
     *
     * @param model the canonical model to validate (may be {@code null})
     * @return a populated {@link ValidationReport} (never {@code null})
     */
    public ValidationReport validate(CanonicalModel model) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (model == null) {
            issues.add(ValidationIssue.error("Canonical model is null"));
            return new ValidationReport(issues);
        }

        // Required fields + unique IDs per entity type.
        validateEntities(issues, "Application", model.applications(), Application::id, Application::name);
        validateEntities(issues, "Interface", model.interfaces(), Interface::id, Interface::name);
        validateEntities(issues, "InformationObject", model.informationObjects(),
                InformationObject::id, InformationObject::informationObject);
        validateEntities(issues, "BusinessProcess", model.businessProcesses(), BusinessProcess::id, BusinessProcess::name);
        // These entities have no meaningful name column; only ids are checked.
        validateIds(issues, "Relationship", model.relationships(), Relationship::id);
        validateIds(issues, "ProcessMapping", model.processMappings(), ProcessMapping::id);
        validateIds(issues, "ApplicationOwnership", model.applicationOwnerships(), ApplicationOwnership::id);
        validateIds(issues, "DataQualityGap", model.dataQualityGaps(), DataQualityGap::id);

        // Required-field completeness per the dataset report's "Req" column.
        for (Application app : model.applications()) {
            if (isBlank(app.businessDomain())) {
                issues.add(ValidationIssue.warning(
                        "Application '" + idLabel(app.id()) + "' is missing required field 'businessDomain'"));
            }
            if (app.businessCriticality() == null) {
                issues.add(ValidationIssue.warning(
                        "Application '" + idLabel(app.id()) + "' is missing required field 'businessCriticality'"));
            }
            if (app.lifecycleStatus() == null) {
                issues.add(ValidationIssue.warning(
                        "Application '" + idLabel(app.id()) + "' is missing required field 'lifecycleStatus'"));
            }
        }
        for (InformationObject info : model.informationObjects()) {
            if (info.classification() == null) {
                issues.add(ValidationIssue.warning(
                        "InformationObject '" + idLabel(info.id()) + "' is missing required field 'classification'"));
            }
        }
        for (Relationship rel : model.relationships()) {
            if (rel.relationshipType() == null) {
                issues.add(ValidationIssue.warning(
                        "Relationship '" + idLabel(rel.id()) + "' is missing required field 'relationshipType'"));
            }
        }

        // Referential integrity: cross-sheet foreign keys -> existing records.
        Set<String> applicationIds = idsOf(model.applications(), Application::id);
        Set<String> interfaceIds = idsOf(model.interfaces(), Interface::id);

        for (Relationship rel : model.relationships()) {
            reference(issues, Severity.ERROR, "Relationship", rel.id(), "target application",
                    rel.targetApplicationId(), applicationIds);
        }
        for (Interface iface : model.interfaces()) {
            reference(issues, Severity.ERROR, "Interface", iface.id(), "consumer application",
                    iface.consumerApplicationId(), applicationIds);
        }
        for (InformationObject info : model.informationObjects()) {
            reference(issues, Severity.ERROR, "InformationObject", info.id(), "source application",
                    info.sourceApplicationId(), applicationIds);
            reference(issues, Severity.ERROR, "InformationObject", info.id(), "target application",
                    info.targetApplicationId(), applicationIds);
            reference(issues, Severity.WARNING, "InformationObject", info.id(), "interface",
                    info.interfaceId(), interfaceIds);
        }
        for (ProcessMapping mapping : model.processMappings()) {
            reference(issues, Severity.ERROR, "ProcessMapping", mapping.id(), "supporting application",
                    mapping.supportingApplicationId(), applicationIds);
        }

        // Ingestion diagnostics (skipped/unrecognized tables) are surfaced as
        // non-blocking warnings rather than lost in the logs.
        for (String note : model.ingestionNotes()) {
            issues.add(ValidationIssue.warning(note));
        }

        log.info("Validation completed: {} issue(s) ({} error(s), {} warning(s))",
                issues.size(),
                issues.stream().filter(i -> i.severity() == Severity.ERROR).count(),
                issues.stream().filter(i -> i.severity() == Severity.WARNING).count());
        return new ValidationReport(issues);
    }

    private <T> void validateEntities(List<ValidationIssue> issues, String type, List<T> entities,
                                      Function<T, String> idFn, Function<T, String> nameFn) {
        Set<String> seenIds = new HashSet<>();
        int index = 0;
        for (T entity : entities) {
            String id = idFn.apply(entity);
            String name = nameFn.apply(entity);

            if (isBlank(id)) {
                issues.add(ValidationIssue.error(
                        type + " at index " + index + " is missing required field 'id'"));
            } else if (!seenIds.add(id)) {
                issues.add(ValidationIssue.error(
                        "Duplicate " + type + " id '" + id + "'"));
            }

            if (isBlank(name)) {
                issues.add(ValidationIssue.error(
                        type + " '" + idLabel(id) + "' is missing required field 'name'"));
            }
            index++;
        }
    }

    private <T> void validateIds(List<ValidationIssue> issues, String type, List<T> entities,
                                 Function<T, String> idFn) {
        Set<String> seenIds = new HashSet<>();
        int index = 0;
        for (T entity : entities) {
            String id = idFn.apply(entity);
            if (isBlank(id)) {
                issues.add(ValidationIssue.error(
                        type + " at index " + index + " is missing required field 'id'"));
            } else if (!seenIds.add(id)) {
                issues.add(ValidationIssue.error("Duplicate " + type + " id '" + id + "'"));
            }
            index++;
        }
    }

    private <T> Set<String> idsOf(List<T> entities, Function<T, String> idFn) {
        Set<String> ids = new HashSet<>();
        for (T entity : entities) {
            String id = idFn.apply(entity);
            if (!isBlank(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * Reference check backed by {@link IngestionSupport#resolveReference}: a
     * blank foreign key is tolerated (nothing to verify), a non-blank id that
     * doesn't resolve (a "ghost" reference) is reported at the given severity.
     */
    private void reference(List<ValidationIssue> issues, Severity severity, String type, String ownerId,
                           String role, String referencedId, Set<String> validIds) {
        IngestionSupport.ReferenceCheck check = IngestionSupport.resolveReference(referencedId, validIds);
        if (check.ghost()) {
            issues.add(new ValidationIssue(severity, type + " '" + idLabel(ownerId) + "' references unknown "
                    + role + " '" + check.id() + "'"));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String idLabel(String id) {
        return isBlank(id) ? "<no id>" : id;
    }
}
