package com.vw.eacontext.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Canonical representation of a directed application-to-application
 * dependency edge — the primary graph edge for the application view.
 *
 * @param id                     unique relationship identifier (required)
 * @param sourceApplicationId    id of the dependent application
 * @param relationshipType       nature of the dependency ({@code depends on} / {@code uses})
 * @param targetApplicationId    id of the depended-upon application (may be a ghost reference)
 * @param dependencyCriticality  how critical this dependency is
 * @param attributes             unrecognized source columns, retained verbatim
 */
@Builder
public record Relationship(
        @NotBlank String id,
        String sourceApplicationId,
        RelationshipType relationshipType,
        String targetApplicationId,
        DependencyCriticality dependencyCriticality,
        Map<String, String> attributes
) {
    public Relationship {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
