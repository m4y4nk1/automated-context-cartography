package com.vw.eacontext.ingestion;

import static com.vw.eacontext.ingestion.IngestionSupport.APPLICATION;
import static com.vw.eacontext.ingestion.IngestionSupport.APPLICATION_OWNERSHIP;
import static com.vw.eacontext.ingestion.IngestionSupport.BUSINESS_PROCESS;
import static com.vw.eacontext.ingestion.IngestionSupport.DATA_QUALITY_GAP;
import static com.vw.eacontext.ingestion.IngestionSupport.INFORMATION_OBJECT;
import static com.vw.eacontext.ingestion.IngestionSupport.INTERFACE;
import static com.vw.eacontext.ingestion.IngestionSupport.PROCESS_MAPPING;
import static com.vw.eacontext.ingestion.IngestionSupport.RELATIONSHIP;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.LifecycleStatus;
import com.vw.eacontext.model.Protocol;

@SpringBootTest
class CsvEaDataParserTest {

    private static final String APPLICATIONS_CSV = """
            ApplicationID,ApplicationName,BusinessDomain,BusinessCriticality,LifecycleStatus,OwnerEmployeeID
            APP-CRM,Customer CRM,Sales,Mission Critical,Active,E001
            APP-BILL,Billing Engine,Finance,Business Critical,Active,
            """;

    private static final String RELATIONSHIPS_CSV = """
            RelationshipID,SourceApplicationID,RelationshipType,TargetApplicationID,DependencyCriticality
            REL-001,APP-CRM,depends on,APP-BILL,High
            """;

    private static final String INTERFACES_CSV = """
            InterfaceID,InterfaceName,ProviderApplicationID,ConsumerApplicationID,Protocol,DataFormat,Frequency,InterfaceStatus
            IF-001,Billing Sync,APP-BILL,APP-ERP,REST/HTTPS,JSON,Real-time,Active
            """;

    private static final String INFORMATION_OBJECTS_CSV = """
            FlowID,InformationObject,Classification,SourceApplicationID,TargetApplicationID,Operation
            FLOW-001,Invoice Data,Confidential,APP-BILL,APP-CRM,Create
            """;

    // A mapping sheet: one row is simultaneously a businessProcess record and a
    // processMapping record (ea.ingestion.excel.sheets/csv.files name both
    // entities against this same table).
    private static final String BUSINESS_PROCESSES_CSV = """
            ProcessMappingID,BusinessProcessID,BusinessProcessName,ProcessDomain,SupportingApplicationID,RoleOfApplication,ProcessCriticality
            BPM-001,BP-01,Order to Cash,Sales,APP-CRM,Primary,Mission Critical
            """;

    private static final String APPLICATION_OWNERSHIP_CSV = """
            OwnershipID,ApplicationID,ApplicationOwner,OwnerEmployeeID,SystemCustodian,BusinessOwner,SupportGroup,Department
            OWN-001,APP-CRM,Priya Nair,E001,Marco Rossi,VP Sales,Sales IT,Sales
            """;

    private static final String DATA_QUALITY_GAPS_CSV = """
            GapID,GapType,EntityType,EntityID,RelatedApplicationID,Description,Severity
            DQ-001,Missing Owner,Application,APP-BILL,APP-BILL,No ownership record,Medium
            """;

    @Autowired
    private CsvEaDataParser parser;

    @Test
    void parsesFromPerEntityStreamMap() {
        Map<String, InputStream> sources = new LinkedHashMap<>();
        sources.put(APPLICATION, stream(APPLICATIONS_CSV));
        sources.put(RELATIONSHIP, stream(RELATIONSHIPS_CSV));
        sources.put(INTERFACE, stream(INTERFACES_CSV));
        sources.put(INFORMATION_OBJECT, stream(INFORMATION_OBJECTS_CSV));
        sources.put(BUSINESS_PROCESS, stream(BUSINESS_PROCESSES_CSV));
        sources.put(PROCESS_MAPPING, stream(BUSINESS_PROCESSES_CSV));
        sources.put(APPLICATION_OWNERSHIP, stream(APPLICATION_OWNERSHIP_CSV));
        sources.put(DATA_QUALITY_GAP, stream(DATA_QUALITY_GAPS_CSV));

        CanonicalModel model = parser.parse(sources);

        assertThat(model.applications()).hasSize(2);
        assertThat(model.relationships()).hasSize(1);
        assertThat(model.interfaces()).hasSize(1);
        assertThat(model.informationObjects()).hasSize(1);
        assertThat(model.businessProcesses()).hasSize(1);
        assertThat(model.processMappings()).hasSize(1);
        assertThat(model.applicationOwnerships()).hasSize(1);
        assertThat(model.dataQualityGaps()).hasSize(1);

        Application crm = model.applications().stream()
                .filter(a -> "APP-CRM".equals(a.id())).findFirst().orElseThrow();
        assertThat(crm.businessDomain()).isEqualTo("Sales");
        assertThat(crm.lifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);

        // Blank owner employee id -> null.
        Application bill = model.applications().stream()
                .filter(a -> "APP-BILL".equals(a.id())).findFirst().orElseThrow();
        assertThat(bill.ownerEmployeeId()).isNull();

        Interface sync = model.interfaces().get(0);
        assertThat(sync.providerApplicationId()).isEqualTo("APP-BILL");
        assertThat(sync.consumerApplicationId()).isEqualTo("APP-ERP");
        assertThat(sync.protocol()).isEqualTo(Protocol.REST_HTTPS);

        assertThat(model.processMappings().get(0).businessProcessId()).isEqualTo("BP-01");
        assertThat(model.processMappings().get(0).supportingApplicationId()).isEqualTo("APP-CRM");
    }

    @Test
    void duplicateHeaderColumnIsNoted() {
        String withDuplicateHeader = """
                ApplicationID,ApplicationName,BusinessDomain,BusinessDomain,BusinessCriticality,LifecycleStatus
                APP-CRM,Customer CRM,Sales,Sales (duplicate col),Mission Critical,Active
                """;
        Map<String, InputStream> sources = new LinkedHashMap<>();
        sources.put(APPLICATION, stream(withDuplicateHeader));

        CanonicalModel model = parser.parse(sources);

        assertThat(model.ingestionNotes()).anyMatch(note -> note.contains("more than one column named")
                && note.contains("BusinessDomain") && note.contains("only the last is used"));
    }

    @Test
    void missingRequiredColumnIsNotedDistinctlyFromABlankValue() {
        // No BusinessDomain/BusinessCriticality/LifecycleStatus column at all —
        // every row will read null for them, which is a structural gap distinct
        // from a column that exists but is blank on some rows.
        String missingRequiredColumns = """
                ApplicationID,ApplicationName
                APP-CRM,Customer CRM
                """;
        Map<String, InputStream> sources = new LinkedHashMap<>();
        sources.put(APPLICATION, stream(missingRequiredColumns));

        CanonicalModel model = parser.parse(sources);

        assertThat(model.ingestionNotes())
                .anyMatch(note -> note.contains("Required column for 'application.businessDomain'"));
        assertThat(model.ingestionNotes())
                .anyMatch(note -> note.contains("Required column for 'application.businessCriticality'"));
        assertThat(model.ingestionNotes())
                .anyMatch(note -> note.contains("Required column for 'application.lifecycleStatus'"));
    }

    @Test
    void parsesFromZipArchiveViaInterface() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("applications.csv", APPLICATIONS_CSV);
        files.put("relationships.csv", RELATIONSHIPS_CSV);
        files.put("interfaces.csv", INTERFACES_CSV);
        files.put("information_objects.csv", INFORMATION_OBJECTS_CSV);
        // One physical file fans out into both businessProcesses and processMappings.
        files.put("business_processes.csv", BUSINESS_PROCESSES_CSV);
        files.put("application_ownership.csv", APPLICATION_OWNERSHIP_CSV);
        files.put("known_data_quality_gaps.csv", DATA_QUALITY_GAPS_CSV);

        byte[] zip = zip(files);
        CanonicalModel model = parser.parse(new ByteArrayInputStream(zip));

        assertThat(model.applications()).hasSize(2);
        assertThat(model.relationships()).hasSize(1);
        assertThat(model.interfaces()).hasSize(1);
        assertThat(model.informationObjects()).hasSize(1);
        assertThat(model.businessProcesses()).hasSize(1);
        assertThat(model.processMappings()).hasSize(1);
        assertThat(model.applicationOwnerships()).hasSize(1);
        assertThat(model.dataQualityGaps()).hasSize(1);
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
