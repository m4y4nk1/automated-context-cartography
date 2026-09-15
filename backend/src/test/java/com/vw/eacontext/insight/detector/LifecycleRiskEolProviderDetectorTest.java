package com.vw.eacontext.insight.detector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.model.LifecycleStatus;

class LifecycleRiskEolProviderDetectorTest {

    private final LifecycleRiskEolProviderDetector detector = new LifecycleRiskEolProviderDetector();

    @Test
    void flagsADeprecatedInterfaceProvidedByAnEndOfLifeApplication() {
        // The Auriga AnswerKey's S13 case: IF-0020 is Deprecated, not Active.
        List<Finding> findings = detector.detect(model(InterfaceStatus.DEPRECATED), null);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).type()).isEqualTo(FindingType.LIFECYCLE_RISK_EOL_PROVIDER);
        assertThat(findings.get(0).relatedEntityIds()).containsExactly("IF-1", "APP-EOL");
        assertThat(findings.get(0).message()).startsWith("Deprecated interface 'IF-1'");
    }

    @Test
    void flagsAnActiveInterfaceProvidedByAnEndOfLifeApplication() {
        List<Finding> findings = detector.detect(model(InterfaceStatus.ACTIVE), null);

        assertThat(findings).extracting(Finding::relatedEntityIds).containsExactly(List.of("IF-1", "APP-EOL"));
    }

    @Test
    void ignoresInterfacesWhoseProviderIsNotEndOfLife() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(app("APP-EOL", LifecycleStatus.PHASE_OUT), app("APP-B", LifecycleStatus.ACTIVE)))
                .interfaces(List.of(iface(InterfaceStatus.DEPRECATED)))
                .build();

        assertThat(detector.detect(model, null)).isEmpty();
    }

    private static CanonicalModel model(InterfaceStatus status) {
        return CanonicalModel.builder()
                .applications(List.of(app("APP-EOL", LifecycleStatus.END_OF_LIFE), app("APP-B", LifecycleStatus.ACTIVE)))
                .interfaces(List.of(iface(status)))
                .build();
    }

    private static Interface iface(InterfaceStatus status) {
        return Interface.builder().id("IF-1").name("Ledger Feed")
                .providerApplicationId("APP-EOL").consumerApplicationId("APP-B")
                .interfaceStatus(status).build();
    }

    private static Application app(String id, LifecycleStatus status) {
        return Application.builder().id(id).name(id).lifecycleStatus(status).build();
    }
}
