package com.vw.eacontext.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.model.LifecycleStatus;
import com.vw.eacontext.model.Protocol;

@SpringBootTest
class ExcelEaDataParserTest {

    @Autowired
    private ExcelEaDataParser parser;

    @Test
    void parsesSampleWorkbookUsingSharedMapping() throws Exception {
        CanonicalModel model;
        try (InputStream in = new ClassPathResource("sample_ea_dataset.xlsx").getInputStream()) {
            model = parser.parse(in);
        }

        // Counts across the per-entity sheets, including the BusinessProcesses
        // mapping sheet fanning out into both businessProcesses and processMappings.
        assertThat(model.applications()).hasSize(12);
        assertThat(model.relationships()).hasSize(13);
        assertThat(model.interfaces()).hasSize(6);
        assertThat(model.informationObjects()).hasSize(5);
        assertThat(model.businessProcesses()).hasSize(3);
        assertThat(model.processMappings()).hasSize(11);
        assertThat(model.applicationOwnerships()).hasSize(9);
        assertThat(model.dataQualityGaps()).hasSize(5);

        Application crm = model.applications().stream()
                .filter(a -> "APP-CRM".equals(a.id())).findFirst().orElseThrow();
        assertThat(crm.name()).isEqualTo("CRM Suite");
        assertThat(crm.businessDomain()).isEqualTo("Sales & Ordering");
        assertThat(crm.lifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);

        Interface legacyExtract = model.interfaces().stream()
                .filter(i -> "IF-004".equals(i.id())).findFirst().orElseThrow();
        assertThat(legacyExtract.providerApplicationId()).isEqualTo("APP-LEGACY");
        assertThat(legacyExtract.consumerApplicationId()).isEqualTo("APP-BILL");
        assertThat(legacyExtract.protocol()).isEqualTo(Protocol.SFTP_FILE);
        assertThat(legacyExtract.interfaceStatus()).isEqualTo(InterfaceStatus.ACTIVE);
    }
}
