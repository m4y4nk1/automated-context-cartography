package com.vw.eacontext.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.CanonicalModel;

/**
 * Exercises the full ingest -&gt; validate pipeline against
 * {@code validation_failure_fixture.json} — a deliberately invalid dataset
 * containing one instance of every row/field-level validation failure mode
 * this app checks for, so a regression in any of them is caught by running
 * this one file rather than needing a hand-built model per check.
 *
 * <p>Structural-level checks (an entire sheet/entity type being empty) aren't
 * exercised here — this fixture populates every entity type on purpose, since
 * those checks are already covered directly against hand-built models in
 * {@link ValidationServiceTest}, and are awkward to combine with "everything
 * else has data" in one file.</p>
 */
@SpringBootTest
class ValidationFailureFixtureTest {

    @Autowired
    private JsonEaDataParser parser;

    @Autowired
    private ValidationService validationService;

    private ValidationReport report;

    @BeforeEach
    void ingestAndValidate() throws Exception {
        try (InputStream in = new ClassPathResource("validation_failure_fixture.json").getInputStream()) {
            CanonicalModel model = parser.parse(in);
            report = validationService.validate(model);
        }
    }

    @Test
    void reportHasErrorsAndIsNeverThrownDespiteEveryFailureMode() {
        assertThat(report.hasErrors()).isTrue();
    }

    @Test
    void duplicateApplicationIdIsAnError() {
        assertThat(report.errors()).anyMatch(i -> "Application".equals(i.sheet())
                && "APP-DUPLICATE".equals(i.recordId()) && i.message().contains("Duplicate"));
    }

    @Test
    void blankApplicationIdIsAnError() {
        assertThat(report.errors()).anyMatch(i -> "Application".equals(i.sheet()) && "id".equals(i.field())
                && i.message().contains("missing required field 'id'"));
    }

    @Test
    void missingApplicationNameIsAnError() {
        assertThat(report.errors()).anyMatch(i -> "Application".equals(i.sheet())
                && "APP-NO-NAME".equals(i.recordId()) && "name".equals(i.field()));
    }

    @Test
    void missingApplicationRequiredAttributeFieldsAreWarnings() {
        assertThat(report.warnings()).anyMatch(i -> "APP-MISSING-REQUIRED-FIELDS".equals(i.recordId())
                && "businessDomain".equals(i.field()));
        assertThat(report.warnings()).anyMatch(i -> "APP-MISSING-REQUIRED-FIELDS".equals(i.recordId())
                && "businessCriticality".equals(i.field()));
        assertThat(report.warnings()).anyMatch(i -> "APP-MISSING-REQUIRED-FIELDS".equals(i.recordId())
                && "lifecycleStatus".equals(i.field()));
    }

    @Test
    void unrecognizedEnumValueIsRejectedSilentlyButNoted() {
        // Parsing behavior: rejected to null, not thrown, not defaulted to a guess.
        assertThat(report.warnings()).anyMatch(i -> i.message().contains("APP-BAD-ENUM")
                && i.message().contains("unrecognized 'businessCriticality'")
                && i.message().contains("Not A Real Criticality Value"));
    }

    @Test
    void unparseableDateIsRejectedSilentlyButNoted() {
        assertThat(report.warnings()).anyMatch(i -> i.message().contains("APP-BAD-DATE")
                && i.message().contains("unparseable 'lifecycleStartDate'")
                && i.message().contains("not-a-real-date"));
    }

    @Test
    void lifecycleEndDateBeforeStartDateIsAWarning() {
        assertThat(report.warnings()).anyMatch(i -> "Application".equals(i.sheet())
                && "APP-BACKWARDS-DATES".equals(i.recordId()) && "lifecycleEndDate".equals(i.field()));
    }

    @Test
    void relationshipBothEndsGhostReportsBothDirections() {
        assertThat(report.errors()).anyMatch(i -> "Relationship".equals(i.sheet())
                && "REL-BOTH-ENDS-GHOST".equals(i.recordId()) && "sourceApplicationId".equals(i.field())
                && i.message().contains("APP-GHOST-REL-SOURCE"));
        assertThat(report.errors()).anyMatch(i -> "Relationship".equals(i.sheet())
                && "REL-BOTH-ENDS-GHOST".equals(i.recordId()) && "targetApplicationId".equals(i.field())
                && i.message().contains("APP-GHOST-REL-TARGET"));
    }

    @Test
    void missingRelationshipTypeIsAWarning() {
        assertThat(report.warnings()).anyMatch(i -> "Relationship".equals(i.sheet())
                && "REL-MISSING-TYPE".equals(i.recordId()) && "relationshipType".equals(i.field()));
    }

    @Test
    void interfaceBothEndsGhostReportsBothDirections() {
        assertThat(report.errors()).anyMatch(i -> "Interface".equals(i.sheet())
                && "IF-BOTH-ENDS-GHOST".equals(i.recordId()) && "providerApplicationId".equals(i.field())
                && i.message().contains("APP-GHOST-IF-PROVIDER"));
        assertThat(report.errors()).anyMatch(i -> "Interface".equals(i.sheet())
                && "IF-BOTH-ENDS-GHOST".equals(i.recordId()) && "consumerApplicationId".equals(i.field())
                && i.message().contains("APP-GHOST-IF-CONSUMER"));
    }

    @Test
    void missingInterfaceNameIsAnError() {
        assertThat(report.errors()).anyMatch(i -> "Interface".equals(i.sheet())
                && "IF-NO-NAME".equals(i.recordId()) && "name".equals(i.field()));
    }

    @Test
    void informationObjectEverythingBrokenReportsEachFkSeparately() {
        assertThat(report.errors()).anyMatch(i -> "InformationObject".equals(i.sheet())
                && "FLOW-EVERYTHING-BROKEN".equals(i.recordId()) && "sourceApplicationId".equals(i.field()));
        assertThat(report.errors()).anyMatch(i -> "InformationObject".equals(i.sheet())
                && "FLOW-EVERYTHING-BROKEN".equals(i.recordId()) && "targetApplicationId".equals(i.field()));
        assertThat(report.warnings()).anyMatch(i -> "InformationObject".equals(i.sheet())
                && "FLOW-EVERYTHING-BROKEN".equals(i.recordId()) && "interfaceId".equals(i.field()));
        assertThat(report.warnings()).anyMatch(i -> "InformationObject".equals(i.sheet())
                && "FLOW-EVERYTHING-BROKEN".equals(i.recordId()) && "classification".equals(i.field()));
    }

    @Test
    void processMappingGhostSupportingApplicationIsAnError() {
        assertThat(report.errors()).anyMatch(i -> "ProcessMapping".equals(i.sheet())
                && "BPM-GHOST-APP".equals(i.recordId()) && "supportingApplicationId".equals(i.field())
                && i.message().contains("APP-GHOST-SUPPORTING"));
    }

    @Test
    void applicationOwnershipGhostApplicationIsAnError() {
        assertThat(report.errors()).anyMatch(i -> "ApplicationOwnership".equals(i.sheet())
                && "OWN-GHOST-APP".equals(i.recordId()) && i.message().contains("APP-GHOST-OWNERSHIP"));
    }

    @Test
    void dataQualityGapMissingGapTypeIsAWarning() {
        assertThat(report.warnings()).anyMatch(i -> "DataQualityGap".equals(i.sheet())
                && "DQ-MISSING-TYPE".equals(i.recordId()) && "gapType".equals(i.field()));
    }

    @Test
    void summaryCountsMatchTheFlatIssueList() {
        ValidationReport.Summary summary = report.summary();
        assertThat(summary.errorCount()).isEqualTo(report.errors().size());
        assertThat(summary.warningCount()).isEqualTo(report.warnings().size());
        assertThat(summary.bySheet().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(report.count());
    }
}
