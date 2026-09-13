package com.vw.eacontext.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.dto.GraphStats;
import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.CanonicalModel;

@SpringBootTest
class SessionModelStoreTest {

    @Autowired
    private JsonEaDataParser parser;

    @Autowired
    private SessionModelStore sessionModelStore;

    /**
     * The seeded sample has 13 Relationships rows, one of which (REL-011)
     * targets the ghost application APP-9001 and is therefore never added as
     * an edge to the dependency graph (see GraphBuilderServiceTest). Even so,
     * the reported relationshipCount must reflect all 13 source rows, not the
     * 12 graph edges — it describes "how many relationships exist in the
     * dataset", the same way interfaceCount/informationObjectCount describe
     * raw row counts rather than only the ones that resolved cleanly. The
     * broken one is separately surfaced via BROKEN_RELATIONSHIP_REFERENCE,
     * not silently dropped from this count.
     */
    @Test
    void relationshipCountReflectsAllRowsIncludingBrokenReferences() throws Exception {
        CanonicalModel model;
        try (InputStream in = new ClassPathResource("sample_ea_dataset.json").getInputStream()) {
            model = parser.parse(in);
        }

        sessionModelStore.load(model);
        GraphStats stats = sessionModelStore.getStats();

        assertThat(model.relationships()).hasSize(13);
        assertThat(stats.relationshipCount()).isEqualTo(13);
    }
}
