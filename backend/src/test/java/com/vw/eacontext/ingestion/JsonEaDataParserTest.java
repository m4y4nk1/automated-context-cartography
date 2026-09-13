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
class JsonEaDataParserTest {

    @Autowired
    private JsonEaDataParser parser;

    @Test
    void parsesSampleDatasetUsingExternalizedMapping() throws Exception {
        CanonicalModel model;
        try (InputStream in = new ClassPathResource("sample_ea_dataset.json").getInputStream()) {
            model = parser.parse(in);
        }

        // Counts match the sample dataset, including the BusinessProcesses
        // mapping array fanning out into both businessProcesses and processMappings.
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
        assertThat(crm.lifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);

        // Enum label normalization: "End of Life" -> END_OF_LIFE.
        Application legacy = model.applications().stream()
                .filter(a -> "APP-LEGACY".equals(a.id())).findFirst().orElseThrow();
        assertThat(legacy.lifecycleStatus()).isEqualTo(LifecycleStatus.END_OF_LIFE);

        Interface deprecatedIface = model.interfaces().stream()
                .filter(i -> "IF-005".equals(i.id())).findFirst().orElseThrow();
        assertThat(deprecatedIface.providerApplicationId()).isEqualTo("APP-ERP");
        assertThat(deprecatedIface.consumerApplicationId()).isEqualTo("APP-MDM");
        assertThat(deprecatedIface.protocol()).isEqualTo(Protocol.JDBC);
        assertThat(deprecatedIface.interfaceStatus()).isEqualTo(InterfaceStatus.DEPRECATED);

        // A ghost reference (consumer absent from Applications) parses to a plain
        // string, never null/omitted and never an ingestion failure.
        Interface ghostConsumer = model.interfaces().stream()
                .filter(i -> "IF-003".equals(i.id())).findFirst().orElseThrow();
        assertThat(ghostConsumer.consumerApplicationId()).isEqualTo("APP-9001");
    }
}
