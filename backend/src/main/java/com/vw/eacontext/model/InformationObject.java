package com.vw.eacontext.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Canonical representation of an information/data flow: a named data object
 * moving from a source application to a target application, optionally over a
 * named {@link Interface}.
 *
 * @param id                    unique flow identifier (required)
 * @param informationObject     the data object name, e.g. "Customer Data" (required)
 * @param classification        data-sensitivity classification (required)
 * @param sourceApplicationId   origin application id
 * @param targetApplicationId   destination application id (may be a ghost reference)
 * @param operation             data operation performed by this flow
 * @param interfaceId           the {@link Interface} the flow travels over (nullable)
 * @param attributes            unrecognized source columns, retained verbatim
 */
@Builder
public record InformationObject(
        @NotBlank String id,
        @NotBlank String informationObject,
        Classification classification,
        String sourceApplicationId,
        String targetApplicationId,
        Operation operation,
        String interfaceId,
        Map<String, String> attributes
) {
    public InformationObject {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
