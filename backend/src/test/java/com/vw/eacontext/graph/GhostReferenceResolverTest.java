package com.vw.eacontext.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.model.RelationshipType;

class GhostReferenceResolverTest {

    private final GhostReferenceResolver resolver = new GhostReferenceResolver();

    @Test
    void cleanModelResolvesNothing() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").name("A").build()))
                .build();

        assertThat(resolver.resolve(model).isEmpty()).isTrue();
    }

    /**
     * ApplicationOwnership.ApplicationID is one of the 7 documented join
     * relationships in the Dataset Report, but was never checked here — the
     * real workbook has zero orphans there today, so this is resilience
     * against a modified dataset, not a fix for a currently-visible bug.
     */
    @Test
    void flagsAnOwnershipRecordReferencingAnUnknownApplication() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").name("A").build()))
                .applicationOwnerships(List.of(ApplicationOwnership.builder()
                        .id("OWN-1").applicationId("APP-UNKNOWN").applicationOwner("Someone").build()))
                .build();

        GhostReferences ghosts = resolver.resolve(model);

        assertThat(ghosts.isGhost("APP-UNKNOWN")).isTrue();
        assertThat(ghosts.sources("APP-UNKNOWN")).containsExactly("OWN-1");
    }

    @Test
    void doesNotFlagAnOwnershipRecordReferencingAKnownApplication() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").name("A").build()))
                .applicationOwnerships(List.of(ApplicationOwnership.builder()
                        .id("OWN-1").applicationId("APP-A").applicationOwner("Someone").build()))
                .build();

        assertThat(resolver.resolve(model).isEmpty()).isTrue();
    }

    /** Sanity check that the pre-existing relationship-based check still works alongside the new one. */
    @Test
    void stillFlagsARelationshipTargetAlongsideAnOwnershipReference() {
        CanonicalModel model = CanonicalModel.builder()
                .applications(List.of(Application.builder().id("APP-A").name("A").build()))
                .relationships(List.of(Relationship.builder()
                        .id("REL-1").sourceApplicationId("APP-A").targetApplicationId("APP-GHOST-1")
                        .relationshipType(RelationshipType.DEPENDS_ON).build()))
                .applicationOwnerships(List.of(ApplicationOwnership.builder()
                        .id("OWN-1").applicationId("APP-GHOST-2").applicationOwner("Someone").build()))
                .build();

        GhostReferences ghosts = resolver.resolve(model);

        assertThat(ghosts.ids()).containsExactlyInAnyOrder("APP-GHOST-1", "APP-GHOST-2");
    }
}
