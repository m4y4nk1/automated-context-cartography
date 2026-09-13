package com.vw.eacontext.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vw.eacontext.ai.SummaryGenerator;
import com.vw.eacontext.ai.TemplateSummaryGenerator;

/**
 * Wiring for the AI summary layer.
 *
 * <p>Registers the deterministic {@link TemplateSummaryGenerator} as the default
 * {@link SummaryGenerator}. It is declared with {@link ConditionalOnMissingBean}
 * so that a future LLM-backed implementation, once provided as a bean, will
 * automatically take precedence without any code change here.</p>
 */
@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnMissingBean(SummaryGenerator.class)
    public SummaryGenerator templateSummaryGenerator() {
        return new TemplateSummaryGenerator();
    }
}

