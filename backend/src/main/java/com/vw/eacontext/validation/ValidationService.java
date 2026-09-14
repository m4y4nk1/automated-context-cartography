package com.vw.eacontext.validation;

import java.time.LocalDate;
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
 *   <li><b>Structural completeness</b> — a sheet/entity with zero rows is
 *       reported as one clear issue, rather than only showing up indirectly as
 *       a flood of unrelated-looking reference errors from every other sheet
 *       that points at it (ERROR for Applications, since almost everything
 *       else foreign-keys against it; INFO for the rest, since a dataset can
 *       legitimately have none of a given record type).</li>
 *   <li><b>Required fields</b> — every entity must have a non-blank {@code id} (and
 *       a name where one applies); ids must be unique within each entity type (ERROR).</li>
 *   <li><b>Completeness</b> — fields the dataset report marks as required
 *       (e.g. an application's business domain/criticality/lifecycle status) (WARNING).</li>
 *   <li><b>Referential integrity</b> — cross-sheet foreign keys must resolve to
 *       an existing record; a ghost id is reported, never thrown on
 *       (ERROR for broken application references, WARNING for a dangling
 *       information-flow-to-interface link).</li>
 *   <li><b>Business rules</b> — cross-field consistency the dataset report
 *       implies, e.g. a lifecycle end date before its start date (WARNING).</li>
 * </ul>
 *
 * <p>This service intentionally covers only structural, required-field,
 * referential and cross-field checks; architectural-pattern detection
 * (ownership gaps, orphans, hubs, lifecycle risk, etc.) lives exclusively in
 * {@code insight/} so the two layers don't duplicate the same logic.</p>
 */
@Slf4j
@Service
public class ValidationService {

    private static final String SHEET_APPLICATION = "Application";
    private static final String SHEET_RELATIONSHIP = "Relationship";
    private static final String SHEET_INTERFACE = "Interface";
    private static final String SHEET_INFORMATION_OBJECT = "InformationObject";
    private static final String SHEET_BUSINESS_PROCESS = "BusinessProcess";
    private static final String SHEET_PROCESS_MAPPING = "ProcessMapping";
    private static final String SHEET_APPLICATION_OWNERSHIP = "ApplicationOwnership";
    private static final String SHEET_DATA_QUALITY_GAP = "DataQualityGap";

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

        validateStructuralCompleteness(issues, model);

        // Required fields + unique IDs per entity type.
        validateEntities(issues, SHEET_APPLICATION, model.applications(), Application::id, Application::name, "name");
        validateEntities(issues, SHEET_INTERFACE, model.interfaces(), Interface::id, Interface::name, "name");
        validateEntities(issues, SHEET_INFORMATION_OBJECT, model.informationObjects(),
                InformationObject::id, InformationObject::informationObject, "informationObject");
        validateEntities(issues, SHEET_BUSINESS_PROCESS, model.businessProcesses(),
                BusinessProcess::id, BusinessProcess::name, "name");
        // These entities have no meaningful name column; only ids are checked.
        validateIds(issues, SHEET_RELATIONSHIP, model.relationships(), Relationship::id);
        validateIds(issues, SHEET_PROCESS_MAPPING, model.processMappings(), ProcessMapping::id);
        validateIds(issues, SHEET_APPLICATION_OWNERSHIP, model.applicationOwnerships(), ApplicationOwnership::id);
        validateIds(issues, SHEET_DATA_QUALITY_GAP, model.dataQualityGaps(), DataQualityGap::id);

        // Required-field completeness per the dataset report's "Req" column.
        for (Application app : model.applications()) {
            if (isBlank(app.businessDomain())) {
                issues.add(ValidationIssue.warning(SHEET_APPLICATION, app.id(), "businessDomain",
                        "Application '" + idLabel(app.id()) + "' is missing required field 'businessDomain'"));
            }
            if (app.businessCriticality() == null) {
                issues.add(ValidationIssue.warning(SHEET_APPLICATION, app.id(), "businessCriticality",
                        "Application '" + idLabel(app.id()) + "' is missing required field 'businessCriticality'"));
            }
            if (app.lifecycleStatus() == null) {
                issues.add(ValidationIssue.warning(SHEET_APPLICATION, app.id(), "lifecycleStatus",
                        "Application '" + idLabel(app.id()) + "' is missing required field 'lifecycleStatus'"));
            }
        }
        for (InformationObject info : model.informationObjects()) {
            if (info.classification() == null) {
                issues.add(ValidationIssue.warning(SHEET_INFORMATION_OBJECT, info.id(), "classification",
                        "InformationObject '" + idLabel(info.id()) + "' is missing required field 'classification'"));
            }
        }
        for (Relationship rel : model.relationships()) {
            if (rel.relationshipType() == null) {
                issues.add(ValidationIssue.warning(SHEET_RELATIONSHIP, rel.id(), "relationshipType",
                        "Relationship '" + idLabel(rel.id()) + "' is missing required field 'relationshipType'"));
            }
        }
        for (DataQualityGap gap : model.dataQualityGaps()) {
            if (isBlank(gap.gapType())) {
                issues.add(ValidationIssue.warning(SHEET_DATA_QUALITY_GAP, gap.id(), "gapType",
                        "DataQualityGap '" + idLabel(gap.id()) + "' is missing required field 'gapType'"));
            }
        }

        // Business rule: an end date before its own start date is a plain
        // date-ordering error, distinct from LifecycleInconsistencyDetector's
        // "Active but the end date has already passed" insight-layer signal.
        for (Application app : model.applications()) {
            LocalDate start = app.lifecycleStartDate();
            LocalDate end = app.lifecycleEndDate();
            if (start != null && end != null && end.isBefore(start)) {
                issues.add(ValidationIssue.warning(SHEET_APPLICATION, app.id(), "lifecycleEndDate",
                        "Application '" + idLabel(app.id()) + "' has a lifecycleEndDate (" + end
                                + ") before its lifecycleStartDate (" + start + ")"));
            }
        }

        // Referential integrity: cross-sheet foreign keys -> existing records.
        Set<String> applicationIds = idsOf(model.applications(), Application::id);
        Set<String> interfaceIds = idsOf(model.interfaces(), Interface::id);
        Set<String> businessProcessIds = idsOf(model.businessProcesses(), BusinessProcess::id);

        for (Relationship rel : model.relationships()) {
            reference(issues, Severity.ERROR, SHEET_RELATIONSHIP, rel.id(), "sourceApplicationId",
                    "source application", rel.sourceApplicationId(), applicationIds);
            reference(issues, Severity.ERROR, SHEET_RELATIONSHIP, rel.id(), "targetApplicationId",
                    "target application", rel.targetApplicationId(), applicationIds);
        }
        for (Interface iface : model.interfaces()) {
            reference(issues, Severity.ERROR, SHEET_INTERFACE, iface.id(), "providerApplicationId",
                    "provider application", iface.providerApplicationId(), applicationIds);
            reference(issues, Severity.ERROR, SHEET_INTERFACE, iface.id(), "consumerApplicationId",
                    "consumer application", iface.consumerApplicationId(), applicationIds);
        }
        for (InformationObject info : model.informationObjects()) {
            reference(issues, Severity.ERROR, SHEET_INFORMATION_OBJECT, info.id(), "sourceApplicationId",
                    "source application", info.sourceApplicationId(), applicationIds);
            reference(issues, Severity.ERROR, SHEET_INFORMATION_OBJECT, info.id(), "targetApplicationId",
                    "target application", info.targetApplicationId(), applicationIds);
            reference(issues, Severity.WARNING, SHEET_INFORMATION_OBJECT, info.id(), "interfaceId",
                    "interface", info.interfaceId(), interfaceIds);
        }
        for (ProcessMapping mapping : model.processMappings()) {
            reference(issues, Severity.ERROR, SHEET_PROCESS_MAPPING, mapping.id(), "supportingApplicationId",
                    "supporting application", mapping.supportingApplicationId(), applicationIds);
            reference(issues, Severity.WARNING, SHEET_PROCESS_MAPPING, mapping.id(), "businessProcessId",
                    "business process", mapping.businessProcessId(), businessProcessIds);
        }
        for (ApplicationOwnership ownership : model.applicationOwnerships()) {
            reference(issues, Severity.ERROR, SHEET_APPLICATION_OWNERSHIP, ownership.id(), "applicationId",
                    "application", ownership.applicationId(), applicationIds);
        }

        // Ingestion diagnostics (skipped/unrecognized tables, unparseable
        // values, missing required columns) are surfaced as non-blocking
        // warnings rather than lost in the logs.
        for (String note : model.ingestionNotes()) {
            issues.add(ValidationIssue.warning(note));
        }

        log.info("Validation completed: {} issue(s) ({} error(s), {} warning(s))",
                issues.size(),
                issues.stream().filter(i -> i.severity() == Severity.ERROR).count(),
                issues.stream().filter(i -> i.severity() == Severity.WARNING).count());
        return new ValidationReport(issues);
    }

    /**
     * A sheet/entity with zero rows is a much clearer signal as one explicit
     * issue than as a flood of downstream reference errors from every other
     * sheet that points at it. Escalated to one overriding error when every
     * single entity type is empty (nothing recognizable was ingested at all —
     * covers both an unrelated file and a genuinely empty one).
     */
    private void validateStructuralCompleteness(List<ValidationIssue> issues, CanonicalModel model) {
        boolean allEmpty = model.applications().isEmpty() && model.relationships().isEmpty()
                && model.interfaces().isEmpty() && model.informationObjects().isEmpty()
                && model.businessProcesses().isEmpty() && model.processMappings().isEmpty()
                && model.applicationOwnerships().isEmpty() && model.dataQualityGaps().isEmpty();
        if (allEmpty) {
            issues.add(ValidationIssue.error(
                    "No recognizable EA dataset content was found in this file "
                            + "— check that you uploaded the correct file"));
            return;
        }
        // Applications is the spine of the model (every other sheet foreign-keys
        // against it), so its absence is an ERROR; the rest are informational —
        // a dataset can legitimately have zero of a given record type.
        noteIfEmpty(issues, Severity.ERROR, SHEET_APPLICATION, model.applications());
        noteIfEmpty(issues, Severity.INFO, SHEET_RELATIONSHIP, model.relationships());
        noteIfEmpty(issues, Severity.INFO, SHEET_INTERFACE, model.interfaces());
        noteIfEmpty(issues, Severity.INFO, SHEET_INFORMATION_OBJECT, model.informationObjects());
        noteIfEmpty(issues, Severity.INFO, SHEET_BUSINESS_PROCESS, model.businessProcesses());
        noteIfEmpty(issues, Severity.INFO, SHEET_PROCESS_MAPPING, model.processMappings());
        noteIfEmpty(issues, Severity.INFO, SHEET_APPLICATION_OWNERSHIP, model.applicationOwnerships());
        noteIfEmpty(issues, Severity.INFO, SHEET_DATA_QUALITY_GAP, model.dataQualityGaps());
    }

    private void noteIfEmpty(List<ValidationIssue> issues, Severity severity, String sheet, List<?> entities) {
        if (entities.isEmpty()) {
            issues.add(new ValidationIssue(severity, "No " + sheet + " records were found in the dataset",
                    sheet, null, null));
        }
    }

    private <T> void validateEntities(List<ValidationIssue> issues, String sheet, List<T> entities,
                                      Function<T, String> idFn, Function<T, String> nameFn, String nameField) {
        Set<String> seenIds = new HashSet<>();
        int index = 0;
        for (T entity : entities) {
            String id = idFn.apply(entity);
            String name = nameFn.apply(entity);

            if (isBlank(id)) {
                issues.add(ValidationIssue.error(sheet, null, "id",
                        sheet + " at index " + index + " is missing required field 'id'"));
            } else if (!seenIds.add(id)) {
                issues.add(ValidationIssue.error(sheet, id, "id",
                        "Duplicate " + sheet + " id '" + id + "'"));
            }

            if (isBlank(name)) {
                issues.add(ValidationIssue.error(sheet, id, nameField,
                        sheet + " '" + idLabel(id) + "' is missing required field '" + nameField + "'"));
            }
            index++;
        }
    }

    private <T> void validateIds(List<ValidationIssue> issues, String sheet, List<T> entities,
                                 Function<T, String> idFn) {
        Set<String> seenIds = new HashSet<>();
        int index = 0;
        for (T entity : entities) {
            String id = idFn.apply(entity);
            if (isBlank(id)) {
                issues.add(ValidationIssue.error(sheet, null, "id",
                        sheet + " at index " + index + " is missing required field 'id'"));
            } else if (!seenIds.add(id)) {
                issues.add(ValidationIssue.error(sheet, id, "id", "Duplicate " + sheet + " id '" + id + "'"));
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
    private void reference(List<ValidationIssue> issues, Severity severity, String sheet, String ownerId,
                           String field, String role, String referencedId, Set<String> validIds) {
        IngestionSupport.ReferenceCheck check = IngestionSupport.resolveReference(referencedId, validIds);
        if (check.ghost()) {
            issues.add(new ValidationIssue(severity, sheet + " '" + idLabel(ownerId) + "' references unknown "
                    + role + " '" + check.id() + "'", sheet, ownerId, field));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String idLabel(String id) {
        return isBlank(id) ? "<no id>" : id;
    }
}
