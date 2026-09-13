package com.vw.eacontext.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The participant guide's governance section requires that any GenAI usage be
 * grounded in the provided data and never assert a relationship the data
 * doesn't support. The default system prompt must say so explicitly rather
 * than relying on the model to infer it from "be factual and specific" alone.
 */
class AzureOpenAiPropertiesTest {

    @Test
    void defaultSystemPromptForbidsHallucinatedRelationships() {
        String prompt = new AzureOpenAiProperties().getSystemPrompt();

        assertThat(prompt).containsIgnoringCase("never assert a dependency, relationship, owner, or risk");
    }
}
