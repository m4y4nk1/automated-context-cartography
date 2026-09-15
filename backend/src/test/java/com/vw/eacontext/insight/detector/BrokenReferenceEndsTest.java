package com.vw.eacontext.insight.detector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.model.RelationshipType;

/** A broken reference can sit on either end of a record — and on both. */
class BrokenReferenceEndsTest {

    private final CanonicalModel model = CanonicalModel.builder()
            .applications(List.of(app("A"), app("B")))
            .relationships(List.of(
                    rel("REL-SOURCE", "GHOST-S", "A"),
                    rel("REL-TARGET", "A", "GHOST-T"),
                    rel("REL-BOTH", "GHOST-X", "GHOST-Y"),
                    rel("REL-OK", "A", "B")))
            .interfaces(List.of(
                    iface("IF-PROVIDER", "GHOST-P", "B"),
                    iface("IF-CONSUMER", "A", "GHOST-C"),
                    iface("IF-OK", "A", "B")))
            .build();

    @Test
    void relationshipsBrokenOnEitherOrBothEndsGetOneFindingEach() {
        List<Finding> findings = new BrokenRelationshipReferenceDetector().detect(model, null);

        assertThat(findings).extracting(f -> f.relatedEntityIds())
                .containsExactly(List.of("REL-SOURCE", "A"), List.of("REL-TARGET", "A"), List.of("REL-BOTH"));
        assertThat(findings.get(0).message()).contains("source application 'GHOST-S'");
        assertThat(findings.get(2).message()).contains("source application 'GHOST-X'", "target application 'GHOST-Y'");
    }

    @Test
    void interfacesBrokenOnTheProviderOrConsumerSideAreBothReported() {
        List<Finding> findings = new DanglingInterfaceConsumerDetector().detect(model, null);

        assertThat(findings).extracting(f -> f.relatedEntityIds())
                .containsExactly(List.of("IF-PROVIDER", "B"), List.of("IF-CONSUMER", "A"));
        assertThat(findings.get(0).message()).contains("provider 'GHOST-P'");
        assertThat(findings.get(1).message()).contains("consumer 'GHOST-C'");
    }

    private static Application app(String id) {
        return Application.builder().id(id).name(id).build();
    }

    private static Relationship rel(String id, String source, String target) {
        return Relationship.builder().id(id).sourceApplicationId(source).targetApplicationId(target)
                .relationshipType(RelationshipType.DEPENDS_ON).build();
    }

    private static Interface iface(String id, String provider, String consumer) {
        return Interface.builder().id(id).name(id).providerApplicationId(provider).consumerApplicationId(consumer).build();
    }
}
