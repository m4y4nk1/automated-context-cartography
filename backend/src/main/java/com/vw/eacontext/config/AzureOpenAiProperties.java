package com.vw.eacontext.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the Azure OpenAI-backed summary generator.
 *
 * <p>Bound from {@code ea.ai.azure.*}. Secrets should be supplied via
 * environment variables (see {@code application-ai.yml}).</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ea.ai.azure")
public class AzureOpenAiProperties {

    /** Base endpoint, e.g. {@code https://my-resource.openai.azure.com}. */
    private String endpoint;

    /** Azure OpenAI API key (sent as the {@code api-key} header). */
    private String apiKey;

    /** The chat model deployment name. */
    private String deployment;

    /** Azure OpenAI REST API version. */
    private String apiVersion = "2024-06-01";

    /** Overall request timeout. */
    private Duration timeout = Duration.ofSeconds(20);

    /** Maximum completion tokens. */
    private int maxTokens = 500;

    /** Sampling temperature (0 = deterministic). */
    private double temperature = 0.2;

    /** System prompt establishing the assistant's role. */
    private String systemPrompt =
            "You are an enterprise architecture analyst. Given landscape statistics and a list of "
                    + "detected findings, write a concise, executive-level summary (max ~8 sentences) "
                    + "highlighting key risks and recommended priorities. Be factual and specific. "
                    + "Only report what the provided statistics and findings actually state — never "
                    + "assert a dependency, relationship, owner, or risk that is not present in the "
                    + "given data.";
}

