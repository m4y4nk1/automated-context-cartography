package com.vw.eacontext.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end web-layer tests. A single cached model is shared across the ordered
 * tests: upload first, then query the derived endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.servlet.multipart.max-file-size=10MB",
        "ea.sample.autoload=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private MockMultipartFile sampleJson() throws Exception {
        try (InputStream in = new ClassPathResource("sample_ea_dataset.json").getInputStream()) {
            return new MockMultipartFile("file", "sample_ea_dataset.json",
                    MediaType.APPLICATION_JSON_VALUE, in.readAllBytes());
        }
    }

    @Test
    @Order(1)
    void queryingBeforeUploadReturns409() throws Exception {
        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @Order(2)
    void uploadReturnsValidationReport() throws Exception {
        mockMvc.perform(multipart("/api/upload").file(sampleJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issues").isArray())
                // The sample seeds ghost-id broken references, which surface as
                // non-blocking validation errors, never an exception.
                .andExpect(jsonPath("$.issues.length()").value(greaterThan(0)));
    }

    @Test
    @Order(3)
    void graphFrameReturnsProjection() throws Exception {
        mockMvc.perform(get("/api/graph/application"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frame").value("application"))
                .andExpect(jsonPath("$.nodes.length()").value(13))
                .andExpect(jsonPath("$.edges.length()").value(24));

        mockMvc.perform(get("/api/graph/infoflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frame").value("informationObject"));
    }

    @Test
    @Order(4)
    void invalidFrameReturns400() throws Exception {
        mockMvc.perform(get("/api/graph/bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @Order(5)
    void impactReturnsBlastRadius() throws Exception {
        mockMvc.perform(get("/api/node/APP-BILL/impact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value("APP-BILL"))
                .andExpect(jsonPath("$.affected.length()").value(5));
    }

    @Test
    @Order(6)
    void impactUnknownNodeReturns404() throws Exception {
        mockMvc.perform(get("/api/node/APP-NOPE/impact"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @Order(7)
    void insightsSummaryAndGaps() throws Exception {
        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").isNotEmpty());

        mockMvc.perform(get("/api/insights/gaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaredCount").value(5))
                .andExpect(jsonPath("$.detectedCount").value(greaterThan(0)));
    }

    @Test
    @Order(8)
    void filtersReturnDropdownOptions() throws Exception {
        mockMvc.perform(get("/api/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domains.length()").value(5));
    }

    @Test
    @Order(9)
    void exportProducesFiles() throws Exception {
        mockMvc.perform(get("/api/export").param("type", "png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("ea-context.png")));

        mockMvc.perform(get("/api/export").param("type", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/export").param("type", "pptx"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(10)
    void invalidExportTypeReturns400() throws Exception {
        mockMvc.perform(get("/api/export").param("type", "svg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
