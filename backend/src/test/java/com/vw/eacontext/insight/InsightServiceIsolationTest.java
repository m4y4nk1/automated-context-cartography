package com.vw.eacontext.insight;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.graph.GraphBuilderService;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.validation.Severity;

class InsightServiceIsolationTest {

    @Test
    void oneFailingDetectorDoesNotDiscardEveryOtherDetectorsFindings() {
        Detector broken = (model, graph) -> {
            throw new IllegalArgumentException("simulated detector bug");
        };
        Detector working = (model, graph) ->
                List.of(new Finding(FindingType.ORPHAN_APPLICATION, Severity.WARNING, List.of("APP-A"), "orphan"));
        InsightService service = new InsightService(new GraphBuilderService(), List.of(broken, working));

        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").name("A").build()))
                .build();

        assertThat(service.analyze(model)).extracting(Finding::type).containsExactly(FindingType.ORPHAN_APPLICATION);
    }
}
