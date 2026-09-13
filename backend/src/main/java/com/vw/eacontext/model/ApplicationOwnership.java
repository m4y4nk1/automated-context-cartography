package com.vw.eacontext.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Canonical representation of an application ownership/accountability record.
 * Independent of {@link Application} — an application can have zero ownership
 * records, and an ownership record can exist with a blank {@code businessOwner}.
 *
 * @param id                unique ownership record identifier (required)
 * @param applicationId     id of the owned {@link Application}
 * @param applicationOwner  named IT owner
 * @param ownerEmployeeId   employee id of the owner
 * @param systemCustodian   technical custodian (person)
 * @param businessOwner     accountable business role (nullable — a known partial gap)
 * @param supportGroup      operational support team
 * @param department        owning department/domain
 * @param attributes        unrecognized source columns, retained verbatim
 */
@Builder
public record ApplicationOwnership(
        @NotBlank String id,
        String applicationId,
        String applicationOwner,
        String ownerEmployeeId,
        String systemCustodian,
        String businessOwner,
        String supportGroup,
        String department,
        Map<String, String> attributes
) {
    public ApplicationOwnership {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
