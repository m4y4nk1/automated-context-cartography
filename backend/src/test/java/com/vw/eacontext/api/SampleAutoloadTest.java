package com.vw.eacontext.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that with autoload enabled (the default) the bundled sample dataset
 * is available immediately, without any upload.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SampleAutoloadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bundledSampleIsAvailableWithoutUpload() throws Exception {
        mockMvc.perform(get("/api/graph/application"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(12))
                .andExpect(jsonPath("$.edges.length()").value(12));

        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").isNotEmpty());
    }
}
