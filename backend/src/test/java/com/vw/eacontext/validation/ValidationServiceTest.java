package com.vw.eacontext.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;

import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.BusinessProcess;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.DataQualityGap;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.model.Protocol;
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.model.RelationshipType;

@SpringBootTest
class ValidationServiceTest {

    @Autowired
    private JsonEaDataParser parser;

    @Autowired
    private ValidationService validationService;

    private CanonicalModel sample;

    @BeforeEach
    void loadSample() throws Exception {
        try (InputStream in = new ClassPathResource("sample_ea_dataset.json").getInputStream()) {
            sample = parser.parse(in);
        }
    }

    @Test
    void ghostReferencesAreReportedAsErrorsNeverThrown() {
        // The sample seeds one ghost id (APP-9001) referenced from four sheets;
        // every one must surface as a non-blocking ERROR, never an exception.
        ValidationReport report = validationService.validate(sample);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(issue -> issue.message().contains("REL-011") && issue.message().contains("APP-9001"));
        assertThat(report.errors())
                .anyMatch(issue -> issue.message().contains("IF-003") && issue.message().contains("APP-9001"));
        assertThat(report.errors())
                .anyMatch(issue -> issue.message().contains("FLOW-005") && issue.message().contains("APP-9001"));
        assertThat(report.errors())
                .anyMatch(issue -> issue.message().contains("BPM-005") && issue.message().contains("APP-9001"));
    }

    @Test
    void ownershipPartialGapIsNotAValidationError() {
        // APP-BILL's ownership record has a blank business owner — that's an
        // insight-layer concern (OwnershipPartialGapDetector), not a validation rule.
        ValidationReport report = validationService.validate(sample);
        assertThat(report.errors()).noneMatch(issue -> issue.message().contains("businessOwner"));
    }

    @Test
    void brokenInterfaceConsumerIsReportedAsError() {
        Interface broken = Interface.builder()
                .id("IF-BROKEN")
                .name("Dangling reference")
                .providerApplicationId("APP-CRM")
                .consumerApplicationId("APP-DOES-NOT-EXIST")
                .protocol(Protocol.REST_HTTPS)
                .interfaceStatus(InterfaceStatus.ACTIVE)
                .build();

        CanonicalModel model = withExtraInterface(sample, broken);
        ValidationReport report = validationService.validate(model);

        assertThat(report.errors())
                .anyMatch(issue -> issue.message().contains("IF-BROKEN")
                        && issue.message().contains("APP-DOES-NOT-EXIST")
                        && issue.message().contains("consumer"));
    }

    @Test
    void duplicateIdIsReportedAsError() {
        Application duplicate = Application.builder()
                .id("APP-CRM") // already exists in the sample
                .name("Duplicate CRM")
                .businessDomain("Sales & Ordering")
                .build();

        CanonicalModel model = withExtraApplication(sample, duplicate);
        ValidationReport report = validationService.validate(model);

        assertThat(report.errors())
                .anyMatch(issue -> issue.message().contains("Duplicate")
                        && issue.message().contains("APP-CRM"));
    }

    @Test
    void emptyModelIsReportedAsOneClearStructuralError() {
        CanonicalModel empty = CanonicalModel.builder().build();

        ValidationReport report = validationService.validate(empty);

        assertThat(report.errors()).hasSize(1);
        assertThat(report.errors().get(0).message()).contains("No recognizable EA dataset content");
        // Not a flood of per-entity "no X records" notes once the single
        // overriding error already says everything needed.
        assertThat(report.issues()).hasSize(1);
    }

    @Test
    void missingApplicationsSheetIsOneClearErrorNotAFloodOfGhostReferences() {
        // A relationship, but zero applications: every application-targeting FK
        // check would otherwise report its own "unknown application" error.
        CanonicalModel model = CanonicalModel.builder()
                .relationships(List.of(Relationship.builder()
                        .id("REL-1").sourceApplicationId("APP-A").targetApplicationId("APP-B")
                        .relationshipType(RelationshipType.DEPENDS_ON).build()))
                .build();

        ValidationReport report = validationService.validate(model);

        assertThat(report.errors())
                .anyMatch(issue -> "Application".equals(issue.sheet())
                        && issue.message().contains("No Application records were found"));
    }

    @Test
    void emptyNonApplicationEntityIsOnlyInfoSeverity() {
        // A dataset can legitimately have zero interfaces — that's not an error.
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").name("A").build()))
                .build();

        ValidationReport report = validationService.validate(model);

        assertThat(report.issuesOf(Severity.INFO))
                .anyMatch(issue -> "Interface".equals(issue.sheet())
                        && issue.message().contains("No Interface records were found"));
        assertThat(report.errors()).noneMatch(issue -> issue.message().contains("Interface records"));
    }

    @Test
    void relationshipSourceApplicationIsNowChecked() {
        // Previously only the target side was validated.
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-B").name("B").build()))
                .relationships(List.of(Relationship.builder()
                        .id("REL-1").sourceApplicationId("APP-GHOST").targetApplicationId("APP-B")
                        .relationshipType(RelationshipType.DEPENDS_ON).build()))
                .build();

        ValidationReport report = validationService.validate(model);

        assertThat(report.errors()).anyMatch(issue -> "Relationship".equals(issue.sheet())
                && "REL-1".equals(issue.recordId()) && "sourceApplicationId".equals(issue.field())
                && issue.message().contains("APP-GHOST"));
    }

    @Test
    void interfaceProviderApplicationIsNowChecked() {
        // Previously only the consumer side was validated.
        Interface iface = Interface.builder()
                .id("IF-1").name("Test").providerApplicationId("APP-GHOST")
                .consumerApplicationId("APP-CRM").protocol(Protocol.REST_HTTPS)
                .interfaceStatus(InterfaceStatus.ACTIVE).build();

        CanonicalModel model = withExtraInterface(sample, iface);
        ValidationReport report = validationService.validate(model);

        assertThat(report.errors()).anyMatch(issue -> "Interface".equals(issue.sheet())
                && "IF-1".equals(issue.recordId()) && "providerApplicationId".equals(issue.field())
                && issue.message().contains("APP-GHOST"));
    }

    @Test
    void applicationOwnershipReferencingUnknownApplicationIsNowChecked() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").name("A").build()))
                .applicationOwnerships(List.of(ApplicationOwnership.builder()
                        .id("OWN-1").applicationId("APP-GHOST").applicationOwner("Someone").build()))
                .build();

        ValidationReport report = validationService.validate(model);

        assertThat(report.errors()).anyMatch(issue -> "ApplicationOwnership".equals(issue.sheet())
                && "OWN-1".equals(issue.recordId()) && issue.message().contains("APP-GHOST"));
    }

    @Test
    void processMappingReferencingUnknownBusinessProcessIsNowChecked() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").name("A").build()))
                .businessProcesses(List.of(BusinessProcess.builder().id("BP-1").name("Real Process").build()))
                .processMappings(List.of(ProcessMapping.builder()
                        .id("BPM-1").businessProcessId("BP-GHOST").supportingApplicationId("APP-A").build()))
                .build();

        ValidationReport report = validationService.validate(model);

        assertThat(report.warnings()).anyMatch(issue -> "ProcessMapping".equals(issue.sheet())
                && "BPM-1".equals(issue.recordId()) && "businessProcessId".equals(issue.field())
                && issue.message().contains("BP-GHOST"));
    }

    @Test
    void dataQualityGapMissingGapTypeIsNowChecked() {
        CanonicalModel model = CanonicalModel.builder()
                .dataQualityGaps(List.of(DataQualityGap.builder().id("DQ-1").build()))
                .build();

        ValidationReport report = validationService.validate(model);

        assertThat(report.warnings()).anyMatch(issue -> "DataQualityGap".equals(issue.sheet())
                && "DQ-1".equals(issue.recordId()) && "gapType".equals(issue.field()));
    }

    @Test
    void lifecycleEndDateBeforeStartDateIsFlagged() {
        Application app = Application.builder()
                .id("APP-BACKWARDS").name("Backwards Dates")
                .lifecycleStartDate(LocalDate.of(2024, 1, 1))
                .lifecycleEndDate(LocalDate.of(2023, 1, 1))
                .build();

        CanonicalModel model = withExtraApplication(sample, app);
        ValidationReport report = validationService.validate(model);

        assertThat(report.warnings()).anyMatch(issue -> "Application".equals(issue.sheet())
                && "APP-BACKWARDS".equals(issue.recordId()) && "lifecycleEndDate".equals(issue.field()));
    }

    @Test
    void reportSummaryGroupsCountsBySeverityAndSheet() {
        ValidationReport report = validationService.validate(sample);

        ValidationReport.Summary summary = report.summary();
        assertThat(summary.errorCount()).isEqualTo(report.errors().size());
        assertThat(summary.warningCount()).isEqualTo(report.warnings().size());
        assertThat(summary.bySheet().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(report.count());
    }

    private static CanonicalModel withExtraInterface(CanonicalModel base, Interface extra) {
        List<Interface> interfaces = new ArrayList<>(base.interfaces());
        interfaces.add(extra);
        return CanonicalModel.builder()
                .applications(base.applications())
                .relationships(base.relationships())
                .interfaces(interfaces)
                .informationObjects(base.informationObjects())
                .businessProcesses(base.businessProcesses())
                .processMappings(base.processMappings())
                .applicationOwnerships(base.applicationOwnerships())
                .dataQualityGaps(base.dataQualityGaps())
                .build();
    }

    private static CanonicalModel withExtraApplication(CanonicalModel base, Application extra) {
        List<Application> applications = new ArrayList<>(base.applications());
        applications.add(extra);
        return CanonicalModel.builder()
                .applications(applications)
                .relationships(base.relationships())
                .interfaces(base.interfaces())
                .informationObjects(base.informationObjects())
                .businessProcesses(base.businessProcesses())
                .processMappings(base.processMappings())
                .applicationOwnerships(base.applicationOwnerships())
                .dataQualityGaps(base.dataQualityGaps())
                .build();
    }
}
