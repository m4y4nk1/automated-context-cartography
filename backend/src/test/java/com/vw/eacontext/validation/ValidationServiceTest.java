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

import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.model.Protocol;

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
