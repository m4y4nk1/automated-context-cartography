package com.vw.eacontext.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.vw.eacontext.config.AzureOpenAiProperties;
import com.vw.eacontext.dto.GraphStats;
import com.vw.eacontext.insight.Finding;

import lombok.extern.slf4j.Slf4j;

/**
 * An {@link SummaryGenerator} that calls an Azure OpenAI chat-completions
 * endpoint via Spring {@link WebClient}.
 *
 * <p>Active only under the {@code ai} Spring profile and marked {@link Primary}
 * so that, when enabled, it supersedes the default
 * {@link TemplateSummaryGenerator} with no other code changes. On any error or
 * timeout it degrades gracefully by delegating to the deterministic template
 * generator, so summaries are always produced.</p>
 */
@Slf4j
@Component
@Primary
@Profile("ai")
public class AzureOpenAiSummaryGenerator implements SummaryGenerator {

    private final AzureOpenAiProperties properties;
    private final WebClient webClient;
    private final SummaryGenerator fallback = new TemplateSummaryGenerator();

    @Autowired
    public AzureOpenAiSummaryGenerator(AzureOpenAiProperties properties) {
        this(properties, WebClient.builder().build());
    }

    /** Constructor for testing with an injected {@link WebClient}. */
    AzureOpenAiSummaryGenerator(AzureOpenAiProperties properties, WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    @Override
    public String summarize(List<Finding> findings, GraphStats stats) {
        try {
            JsonNode response = webClient.post()
                    .uri(buildUrl())
                    .header("api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(buildRequestBody(findings, stats))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(properties.getTimeout())
                    .block();

            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Azure OpenAI returned an empty completion");
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("Azure OpenAI summary failed ({}); falling back to template summary",
                    e.toString());
            return fallback.summarize(findings, stats);
        }
    }

    private String buildUrl() {
        String base = properties.getEndpoint() == null ? "" : properties.getEndpoint().replaceAll("/+$", "");
        return base + "/openai/deployments/" + properties.getDeployment()
                + "/chat/completions?api-version=" + properties.getApiVersion();
    }

    private Map<String, Object> buildRequestBody(List<Finding> findings, GraphStats stats) {
        return Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", properties.getSystemPrompt()),
                        Map.of("role", "user", "content", buildUserPrompt(findings, stats))),
                "temperature", properties.getTemperature(),
                "max_tokens", properties.getMaxTokens());
    }

    private String buildUserPrompt(List<Finding> findings, GraphStats stats) {
        StringBuilder sb = new StringBuilder();
        if (stats != null) {
            sb.append("Landscape statistics:\n")
                    .append("- applications: ").append(stats.applicationCount()).append('\n')
                    .append("- relationships: ").append(stats.relationshipCount()).append('\n')
                    .append("- interfaces: ").append(stats.interfaceCount()).append('\n')
                    .append("- domains: ").append(stats.domainCount()).append('\n')
                    .append("- business processes: ").append(stats.businessProcessCount()).append('\n')
                    .append("- information flows: ").append(stats.informationObjectCount()).append('\n')
                    .append("- hub applications: ").append(stats.hubCount()).append('\n')
                    .append("- circular dependency chains: ").append(stats.cycleCount()).append('\n');
            if (stats.mostConnectedApplicationId() != null) {
                sb.append("- most connected application: ").append(stats.mostConnectedApplicationId())
                        .append(" (in-degree ").append(stats.maxDegree()).append(")\n");
            }
        }
        sb.append("\nFindings (type | severity | related entities | message):\n");
        if (findings == null || findings.isEmpty()) {
            sb.append("- none\n");
        } else {
            for (Finding f : findings) {
                sb.append("- ").append(f.type()).append(" | ").append(f.severity())
                        .append(" | ").append(f.relatedEntityIds()).append(" | ").append(f.message()).append('\n');
            }
        }
        sb.append("\nWrite the executive summary now.");
        return sb.toString();
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).path("message").path("content").asText(null);
    }
}

