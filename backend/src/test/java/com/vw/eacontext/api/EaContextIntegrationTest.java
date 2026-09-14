package com.vw.eacontext.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.validation.Severity;

/**
 * Full-stack integration test: boots the application (embedded server) and hits
 * the REST endpoints over HTTP against the auto-loaded bundled sample dataset.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EaContextIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void applicationGraphHasExpectedNodeAndEdgeCounts() {
        ResponseEntity<GraphDto> response = rest.getForEntity("/api/graph/application", GraphDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        GraphDto graph = response.getBody();
        assertThat(graph).isNotNull();
        assertThat(graph.frame()).isEqualTo("application");
        // 12 applications + the APP-9001 placeholder; 24 relationship/interface/
        // flow records, each its own edge.
        assertThat(graph.nodes()).hasSize(13);
        assertThat(graph.edges()).hasSize(24);
    }

    @Test
    void insightsContainKnownFindings() {
        ResponseEntity<Finding[]> response = rest.getForEntity("/api/insights", Finding[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Finding> findings = Arrays.asList(response.getBody());

        // APP-OMS is the hub (in-degree 7 > threshold 5).
        assertThat(idsOf(findings, FindingType.HUB)).contains("APP-OMS");

        // APP-ERP, APP-DUPLICATE-B and APP-INCONSISTENT have no ownership record.
        assertThat(idsOf(findings, FindingType.OWNERSHIP_RECORD_MISSING))
                .containsExactlyInAnyOrder("APP-ERP", "APP-DUPLICATE-B", "APP-INCONSISTENT");

        // APP-LEGACY is end-of-life and supports the mission-critical BP-01 process.
        List<Finding> criticalLifecycle = findings.stream()
                .filter(f -> f.type() == FindingType.LIFECYCLE_RISK_CRITICAL_PROCESS)
                .toList();
        assertThat(criticalLifecycle).isNotEmpty();
        assertThat(criticalLifecycle).allMatch(f -> f.severity() == Severity.ERROR);
        assertThat(criticalLifecycle.get(0).relatedEntityIds()).contains("APP-LEGACY");
    }

    private List<String> idsOf(List<Finding> findings, FindingType type) {
        return findings.stream()
                .filter(f -> f.type() == type)
                .flatMap(f -> f.relatedEntityIds().stream())
                .toList();
    }
}
