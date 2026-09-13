package com.vw.eacontext.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Canonical representation of an application (system) in the EA landscape —
 * the node table. Ownership and business-process mapping are separate joined
 * entities ({@link ApplicationOwnership}, {@link ProcessMapping}); this record
 * only carries the application's own master data.
 *
 * @param id                   unique application identifier (required)
 * @param name                 human-readable application name (required, not
 *                             guaranteed unique — see {@code DuplicateApplicationDetector})
 * @param description          one-line purpose of the application
 * @param businessDomain       business domain the application belongs to
 * @param businessCriticality  business importance
 * @param lifecycleStatus      current {@link LifecycleStatus lifecycle state}
 * @param lifecycleStartDate   date the app entered its current lifecycle (nullable)
 * @param lifecycleEndDate     date of planned retirement; may be in the past (nullable)
 * @param hosting              where the application is hosted
 * @param vendorType           purchased product vs. built in-house
 * @param ownerEmployeeId      employee id of the owner (blank signals an ownership gap)
 * @param costCenter           charge-back cost centre
 * @param attributes           unrecognized source columns, retained verbatim
 */
@Builder
public record Application(
        @NotBlank String id,
        @NotBlank String name,
        String description,
        String businessDomain,
        BusinessCriticality businessCriticality,
        LifecycleStatus lifecycleStatus,
        LocalDate lifecycleStartDate,
        LocalDate lifecycleEndDate,
        Hosting hosting,
        VendorType vendorType,
        String ownerEmployeeId,
        String costCenter,
        Map<String, String> attributes
) {
    public Application {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
