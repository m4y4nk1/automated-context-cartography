package com.vw.eacontext.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Canonical representation of an integration between a provider and a
 * consumer application.
 *
 * @param id                      unique interface identifier (required)
 * @param name                    human-readable interface name (required)
 * @param providerApplicationId   id of the application that exposes the interface
 * @param consumerApplicationId   id of the application that consumes it (may be a ghost reference)
 * @param protocol                technical integration protocol
 * @param dataFormat              payload encoding
 * @param frequency               exchange cadence
 * @param interfaceStatus         operational status
 * @param attributes              unrecognized source columns, retained verbatim
 */
@Builder
public record Interface(
        @NotBlank String id,
        @NotBlank String name,
        String providerApplicationId,
        String consumerApplicationId,
        Protocol protocol,
        DataFormat dataFormat,
        Frequency frequency,
        InterfaceStatus interfaceStatus,
        Map<String, String> attributes
) {
    public Interface {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
