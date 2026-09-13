package com.vw.eacontext.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SummaryGeneratorWiringTest {

    @Autowired
    private SummaryGenerator summaryGenerator;

    @Test
    void templateGeneratorIsTheDefaultBean() {
        assertThat(summaryGenerator).isInstanceOf(TemplateSummaryGenerator.class);
    }
}

