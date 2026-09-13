package com.vw.eacontext.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Canonical representation of a business process. Supporting applications are
 * not embedded here — they live in {@link ProcessMapping}, since one process
 * can have many supporting applications.
 *
 * @param id             unique business process identifier (required, repeats
 *                        across its supporting-application mapping rows)
 * @param name           human-readable process name (required)
 * @param processDomain  business grouping, e.g. Sales, Aftersales, Finance
 * @param attributes     unrecognized source columns, retained verbatim
 */
@Builder
public record BusinessProcess(
        @NotBlank String id,
        @NotBlank String name,
        String processDomain,
        Map<String, String> attributes
) {
    public BusinessProcess {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
