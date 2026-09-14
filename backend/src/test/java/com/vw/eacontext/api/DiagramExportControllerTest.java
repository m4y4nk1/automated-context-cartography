package com.vw.eacontext.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer coverage for the interoperability exports. Kept separate from
 * {@link EaControllerTest} because that class is order-dependent.
 *
 * <p>Reads the auto-loaded bundled sample rather than uploading one, matching
 * {@link SampleAutoloadTest}'s configuration. That matters: {@code SessionModelStore}
 * is a singleton, and Spring shares a cached context between test classes with
 * identical configuration — so a class that uploads under
 * {@code ea.sample.autoload=false} would leak its model into
 * {@link ApiErrorAndCorsTest}'s "nothing loaded yet -&gt; 409" assertion. Keeping
 * this class read-only avoids that entirely.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramExportControllerTest {

    private static final String BODY = """
            {"frame":"application","placements":{
              "APP-CRM":{"x":0,"y":0,"w":148,"h":66,"color":"#4f9d8f"},
              "APP-OMS":{"x":300,"y":200,"w":148,"h":66,"color":"#c98a3c"}
            }}""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void drawioExportDownloadsXml() throws Exception {
        mockMvc.perform(post("/api/export/diagram/drawio")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("ea-context-application.drawio")))
                .andExpect(content().string(Matchers.containsString("<mxfile")))
                .andExpect(content().string(Matchers.containsString("n-APP-CRM")));
    }

    @Test
    void plantUmlExportDownloadsText() throws Exception {
        mockMvc.perform(post("/api/export/diagram/puml")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("ea-context-application.puml")))
                .andExpect(content().string(Matchers.containsString("@startuml")));
    }

    @Test
    void unknownFormatReturns400() throws Exception {
        mockMvc.perform(post("/api/export/diagram/xmi")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownFrameReturns400() throws Exception {
        mockMvc.perform(post("/api/export/diagram/drawio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frame\":\"not-a-frame\",\"placements\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
