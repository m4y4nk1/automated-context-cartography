package com.vw.eacontext.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Web-layer coverage for the anchor-scoped {@code /api/graph/{frame}}
 * addition — the fix for the audit's "F3: correctly scoped, not the whole
 * landscape" gap.
 *
 * <p>Read-only against the auto-loaded bundled sample, matching
 * {@link SampleAutoloadTest} and {@link DiagramExportControllerTest}'s
 * documented reasoning: {@code SessionModelStore} is a singleton and Spring
 * shares a cached context between identically-configured test classes, so a
 * class that uploads would leak its model into other tests' assumptions.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScopedGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void anchoredApplicationRequestReturnsAStrictSubsetContainingTheAnchor() throws Exception {
        MvcResult full = mockMvc.perform(get("/api/graph/application")).andExpect(status().isOk()).andReturn();
        MvcResult scoped = mockMvc.perform(get("/api/graph/application").param("anchor", "APP-CRM").param("depth", "1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode fullBody = mapper.readTree(full.getResponse().getContentAsString());
        JsonNode scopedBody = mapper.readTree(scoped.getResponse().getContentAsString());

        int fullNodeCount = fullBody.get("nodes").size();
        int scopedNodeCount = scopedBody.get("nodes").size();
        assertThat(scopedNodeCount).isLessThan(fullNodeCount);
        assertThat(scopedBody.get("edges").size()).isLessThan(fullBody.get("edges").size());

        boolean containsAnchor = false;
        for (JsonNode node : scopedBody.get("nodes")) {
            if ("APP-CRM".equals(node.get("id").asText())) {
                containsAnchor = true;
            }
        }
        assertThat(containsAnchor).isTrue();
    }

    @Test
    void noAnchorReturnsTheFullFrameUnchanged() throws Exception {
        // Backward compatibility: the exact same response SampleAutoloadTest pins.
        mockMvc.perform(get("/api/graph/application"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(13))
                .andExpect(jsonPath("$.edges.length()").value(24));
    }

    @Test
    void domainAnchorScopesToThatDomainsApplicationsNotDomainBubbles() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/graph/domain")
                        .param("anchor", "Sales & Ordering").param("depth", "1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        // Every node must be an application (or ghost placeholder) — never a
        // domain-aggregate bubble, which is what an anchor-less domain call returns.
        for (JsonNode node : body.get("nodes")) {
            assertThat(node.get("type").asText()).isIn("application", "applicationGhost");
        }
    }

    @Test
    void unknownAnchorReturns404() throws Exception {
        mockMvc.perform(get("/api/graph/application").param("anchor", "NOT-A-REAL-ID"))
                .andExpect(status().isNotFound());
    }

    @Test
    void depthOutOfRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/graph/application").param("anchor", "APP-CRM").param("depth", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/graph/application").param("anchor", "APP-CRM").param("depth", "5"))
                .andExpect(status().isBadRequest());
    }
}
