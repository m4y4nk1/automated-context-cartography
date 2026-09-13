package com.vw.eacontext.dto;

import java.util.List;

import lombok.Builder;

/**
 * The distinct, selectable values for each Auriga filter dimension, served by
 * {@code GET /api/filters} so a UI can populate its pickers without
 * downloading a full graph projection. Every list reflects only the values
 * actually present in the loaded dataset.
 *
 * @param domains                business domains present in the dataset
 * @param businessCriticalities  business-criticality values present
 * @param lifecycleStatuses      lifecycle statuses present
 * @param hostings               hosting models present
 * @param vendorTypes            vendor types present
 * @param classifications        information-object classifications present
 * @param protocols              interface protocols present
 */
@Builder
public record FilterOptions(
        List<Option> domains,
        List<Option> businessCriticalities,
        List<Option> lifecycleStatuses,
        List<Option> hostings,
        List<Option> vendorTypes,
        List<Option> classifications,
        List<Option> protocols) {

    /**
     * A single selectable entry.
     *
     * @param value the value submitted back as a filter query parameter
     * @param label the human-readable text shown in the picker
     */
    public record Option(String value, String label) {
    }

    /** @return an instance with every dimension empty. */
    public static FilterOptions empty() {
        List<Option> none = List.of();
        return FilterOptions.builder()
                .domains(none).businessCriticalities(none).lifecycleStatuses(none)
                .hostings(none).vendorTypes(none).classifications(none).protocols(none)
                .build();
    }
}
