package com.vw.eacontext.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.config.AzureOpenAiProperties;
import com.vw.eacontext.dto.GraphStats;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.validation.Severity;

class AzureOpenAiSummaryGeneratorTest {

    private static final GraphStats STATS = GraphStats.builder()
            .applicationCount(12).relationshipCount(12).interfaceCount(6).domainCount(5)
            .businessProcessCount(3).informationObjectCount(5)
            .mostConnectedApplicationId("APP-OMS").maxDegree(7)
            .hubCount(1).cycleCount(1).orphanCount(1)
            .build();

    private static final List<Finding> FINDINGS = List.of(
            new Finding(FindingType.OWNERSHIP_RECORD_MISSING, Severity.WARNING, List.of("APP-ERP"), "no ownership record"),
            new Finding(FindingType.HUB, Severity.ERROR, List.of("APP-OMS"), "hub"));

    @Test
    void fallsBackToTemplateWhenEndpointUnreachable() {
        AzureOpenAiProperties props = new AzureOpenAiProperties();
        props.setEndpoint("http://localhost:1"); // nothing listening -> connection refused
        props.setApiKey("dummy");
        props.setDeployment("dummy");
        props.setTimeout(Duration.ofSeconds(2));

        AzureOpenAiSummaryGenerator azure = new AzureOpenAiSummaryGenerator(props);

        String result = azure.summarize(FINDINGS, STATS);

        // On failure it must return the deterministic template output, not throw.
        String expectedTemplate = new TemplateSummaryGenerator().summarize(FINDINGS, STATS);
        assertThat(result).isEqualTo(expectedTemplate);
    }
}
