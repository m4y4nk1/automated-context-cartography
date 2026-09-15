package com.vw.eacontext.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.vw.eacontext.model.CanonicalModel;

/**
 * Tables whose names match nothing in configuration are bound by header
 * signature. A process-to-application mapping table carries two entities —
 * the mapping rows and the processes — and must still yield both.
 */
@SpringBootTest
class CompanionBindingTest {

    // The data contract's required application columns, under non-configured
    // spellings — enough for the table to be recognized by its headers alone.
    private static final String APPS = """
            Application ID,Application Name,Business Domain,Business Criticality,Lifecycle Status
            APP-A,Alpha,Sales,Mission Critical,Active
            APP-B,Beta,Finance,Business Critical,Active
            """;

    @Autowired
    private CsvEaDataParser parser;

    @Test
    void aRenamedMappingTableYieldsBothItsProcessesAndItsMappings() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("application inventory.csv", APPS);
        files.put("process support matrix.csv", """
                Process Mapping ID,Business Process ID,Business Process Name,Process Domain,Supporting Application ID,Role Of Application,Process Criticality
                MAP-1,PROC-1,Order to Cash,Sales,APP-A,Primary,Mission Critical
                MAP-2,PROC-1,Order to Cash,Sales,APP-B,Supporting,Mission Critical
                """);

        CanonicalModel model = parser.parse(new ByteArrayInputStream(zip(files)));

        assertThat(model.processMappings()).hasSize(2);
        assertThat(model.businessProcesses()).hasSize(1);
        assertThat(model.businessProcesses().get(0).name()).isEqualTo("Order to Cash");
    }

    @Test
    void aSeparateProcessCatalogueAndMappingTableDoNotBleedIntoEachOther() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("application inventory.csv", APPS);
        files.put("process catalogue.csv", """
                Business Process ID,Business Process Name,Process Domain
                PROC-1,Order to Cash,Sales
                PROC-2,Record to Report,Finance
                """);
        files.put("process mappings.csv", """
                Process Mapping ID,Business Process ID,Supporting Application ID,Role Of Application,Process Criticality
                MAP-1,PROC-1,APP-A,Primary,Mission Critical
                """);

        CanonicalModel model = parser.parse(new ByteArrayInputStream(zip(files)));

        assertThat(model.businessProcesses()).extracting(p -> p.id()).containsExactlyInAnyOrder("PROC-1", "PROC-2");
        assertThat(model.processMappings()).extracting(m -> m.id()).containsExactly("MAP-1");
    }

    @Test
    void anOwnershipTableNeverCreatesApplications() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("application inventory.csv", APPS);
        files.put("owners.csv", """
                Ownership ID,Application ID,Application Name,Application Owner,Owner Employee ID,System Custodian,Business Owner,Support Group,Department
                OWN-1,APP-A,Alpha,Priya,E1,Marco,VP Sales,Sales IT,Sales
                """);

        CanonicalModel model = parser.parse(new ByteArrayInputStream(zip(files)));

        assertThat(model.applications()).hasSize(2);
        assertThat(model.applicationOwnerships()).hasSize(1);
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
