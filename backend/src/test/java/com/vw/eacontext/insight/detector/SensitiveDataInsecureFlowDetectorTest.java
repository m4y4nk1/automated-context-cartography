package com.vw.eacontext.insight.detector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.FindingType;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Classification;
import com.vw.eacontext.model.Hosting;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.model.Protocol;

class SensitiveDataInsecureFlowDetectorTest {

    private final SensitiveDataInsecureFlowDetector detector = new SensitiveDataInsecureFlowDetector();

    /**
     * The target application resolving to a public-facing (Public Cloud) app is
     * named in the finding — the spec's "landing in a public-facing app" clause.
     */
    @Test
    void namesAPublicFacingTarget() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-WEB").hosting(Hosting.PUBLIC_CLOUD).build()))
                .interfaces(List.of(Interface.builder()
                        .id("IF-1").providerApplicationId("APP-CRM").consumerApplicationId("APP-WEB")
                        .protocol(Protocol.SFTP_FILE).interfaceStatus(InterfaceStatus.DEPRECATED).build()))
                .informationObjects(List.of(InformationObject.builder()
                        .id("FLOW-1").informationObject("Customer Data").classification(Classification.CONFIDENTIAL_PII)
                        .sourceApplicationId("APP-CRM").targetApplicationId("APP-WEB").interfaceId("IF-1").build()))
                .build();

        List<Finding> findings = detector.detect(model, null);

        assertThat(findings).hasSize(1);
        Finding finding = findings.get(0);
        assertThat(finding.relatedEntityIds()).contains("APP-WEB");
        assertThat(finding.message()).contains("public-facing").contains("APP-WEB");
    }

    /**
     * A target hosted internally (or unresolved/ghost) is still flagged — the
     * trigger never depends on hosting — but the message doesn't claim it's
     * public-facing.
     */
    @Test
    void stillFlagsAnInternalOrUnresolvedTargetWithoutClaimingPublicFacing() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-INTERNAL").hosting(Hosting.ON_PREM).build()))
                .interfaces(List.of(Interface.builder()
                        .id("IF-1").providerApplicationId("APP-CRM").consumerApplicationId("APP-INTERNAL")
                        .protocol(Protocol.SFTP_FILE).interfaceStatus(InterfaceStatus.ACTIVE).build()))
                .informationObjects(List.of(InformationObject.builder()
                        .id("FLOW-1").informationObject("Customer Data").classification(Classification.RESTRICTED_PCI)
                        .sourceApplicationId("APP-CRM").targetApplicationId("APP-9999").interfaceId("IF-1").build()))
                .build();

        List<Finding> findings = detector.detect(model, null);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).type()).isEqualTo(FindingType.SENSITIVE_DATA_INSECURE_FLOW);
        assertThat(findings.get(0).message()).doesNotContain("public-facing");
    }

    @Test
    void doesNotFlagInternalClassificationOrSecureActiveInterface() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").build()))
                .interfaces(List.of(Interface.builder()
                        .id("IF-1").providerApplicationId("APP-CRM").consumerApplicationId("APP-A")
                        .protocol(Protocol.REST_HTTPS).interfaceStatus(InterfaceStatus.ACTIVE).build()))
                .informationObjects(List.of(InformationObject.builder()
                        .id("FLOW-1").informationObject("Customer Data").classification(Classification.CONFIDENTIAL_PII)
                        .sourceApplicationId("APP-CRM").targetApplicationId("APP-A").interfaceId("IF-1").build()))
                .build();

        assertThat(detector.detect(model, null)).isEmpty();
    }
}
