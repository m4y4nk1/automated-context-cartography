package com.vw.eacontext.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Canonical representation of a business-process-to-application mapping — the
 * many-to-many join between {@link BusinessProcess} and {@link Application}.
 * One process can have many supporting applications, and one application can
 * support many processes.
 *
 * @param id                       unique process-mapping identifier (required)
 * @param businessProcessId        id of the {@link BusinessProcess} being supported
 * @param supportingApplicationId  id of the supporting application (may be a ghost reference)
 * @param roleOfApplication        whether the application is the primary or a supporting system
 * @param processCriticality       criticality of the process from this mapping's perspective
 *                                 (a 3-value scale — see {@link ProcessCriticality})
 * @param attributes               unrecognized source columns, retained verbatim
 */
@Builder
public record ProcessMapping(
        @NotBlank String id,
        String businessProcessId,
        String supportingApplicationId,
        RoleOfApplication roleOfApplication,
        ProcessCriticality processCriticality,
        Map<String, String> attributes
) {
    public ProcessMapping {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
