package com.vw.eacontext.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.vw.eacontext.dto.FilterOptions;
import com.vw.eacontext.dto.FilterOptions.Option;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;

import lombok.extern.slf4j.Slf4j;

/**
 * Derives the selectable values for each Auriga filter dimension from the
 * loaded {@link CanonicalModel}, so a UI can populate its pickers from one
 * small response instead of scraping a full graph projection.
 */
@Slf4j
@Service
public class FilterOptionsService {

    /**
     * Builds the option lists for the given model.
     *
     * @param model the loaded canonical model
     * @return the distinct options actually present in the dataset
     */
    public FilterOptions optionsFor(CanonicalModel model) {
        if (model == null) {
            return FilterOptions.empty();
        }
        return FilterOptions.builder()
                .domains(domains(model))
                .businessCriticalities(criticalities(model))
                .lifecycleStatuses(lifecycles(model))
                .hostings(hostings(model))
                .vendorTypes(vendorTypes(model))
                .classifications(classifications(model))
                .protocols(protocols(model))
                .build();
    }

    private List<Option> domains(CanonicalModel model) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Application app : model.applications()) {
            if (app.businessDomain() != null && !app.businessDomain().isBlank()) {
                values.putIfAbsent(app.businessDomain(), app.businessDomain());
            }
        }
        return sorted(values);
    }

    private List<Option> criticalities(CanonicalModel model) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Application app : model.applications()) {
            if (app.businessCriticality() != null) {
                values.putIfAbsent(app.businessCriticality().name(), label(app.businessCriticality().name()));
            }
        }
        return sorted(values);
    }

    private List<Option> lifecycles(CanonicalModel model) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Application app : model.applications()) {
            if (app.lifecycleStatus() != null) {
                values.putIfAbsent(app.lifecycleStatus().name(), app.lifecycleStatus().label());
            }
        }
        return sorted(values);
    }

    private List<Option> hostings(CanonicalModel model) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Application app : model.applications()) {
            if (app.hosting() != null) {
                values.putIfAbsent(app.hosting().name(), label(app.hosting().name()));
            }
        }
        return sorted(values);
    }

    private List<Option> vendorTypes(CanonicalModel model) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Application app : model.applications()) {
            if (app.vendorType() != null) {
                values.putIfAbsent(app.vendorType().name(), label(app.vendorType().name()));
            }
        }
        return sorted(values);
    }

    private List<Option> classifications(CanonicalModel model) {
        Map<String, String> values = new LinkedHashMap<>();
        for (InformationObject info : model.informationObjects()) {
            if (info.classification() != null) {
                values.putIfAbsent(info.classification().name(), label(info.classification().name()));
            }
        }
        return sorted(values);
    }

    private List<Option> protocols(CanonicalModel model) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Interface iface : model.interfaces()) {
            if (iface.protocol() != null) {
                values.putIfAbsent(iface.protocol().name(), label(iface.protocol().name()));
            }
        }
        return sorted(values);
    }

    /** Enum constant tokens kept fully upper-cased rather than title-cased. */
    private static final Set<String> ACRONYMS =
            Set.of("SAAS", "COTS", "PII", "PCI", "SFTP", "JDBC", "REST", "HTTPS", "SOAP");

    /** {@code MISSION_CRITICAL -> "Mission Critical"}, {@code CONFIDENTIAL_PII -> "Confidential PII"}. */
    private String label(String enumName) {
        String[] words = enumName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (ACRONYMS.contains(word)) {
                sb.append(word);
            } else {
                String lower = word.toLowerCase(Locale.ROOT);
                sb.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
        }
        return sb.toString();
    }

    private List<Option> sorted(Map<String, String> byValue) {
        List<Option> options = new ArrayList<>(byValue.size());
        byValue.forEach((value, lbl) -> options.add(new Option(value, lbl)));
        options.sort(Comparator.comparing(Option::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(options);
    }
}
