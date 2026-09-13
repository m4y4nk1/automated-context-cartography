package com.vw.eacontext.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vw.eacontext.config.EaIngestionProperties;
import com.vw.eacontext.exception.EaIngestionException;
import com.vw.eacontext.model.CanonicalModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link EaDataParser} for JSON sources, backed by Jackson.
 *
 * <p>Every top-level array is bound to one or more entity types by
 * {@link IngestionSupport#entitiesByTableName}/{@link IngestionSupport#bind}:
 * first by its configured array name (a mapping-style array such as Auriga's
 * {@code businessProcesses} can bind to more than one entity), otherwise by
 * the header signature of its objects. Arrays that match nothing are skipped
 * with a non-blocking note, so arbitrary EA-shaped JSON still ingests.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonEaDataParser implements EaDataParser {

    private final ObjectMapper objectMapper;
    private final EaIngestionProperties properties;

    @Override
    public CanonicalModel parse(InputStream in) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(in);
        } catch (IOException e) {
            throw new EaIngestionException("Failed to read JSON EA dataset", e);
        }
        if (root == null || root.isNull()) {
            throw new EaIngestionException("JSON EA dataset is empty");
        }

        IngestionSupport.Accumulator accumulator = new IngestionSupport.Accumulator();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode array = entry.getValue();
            if (array == null || !array.isArray() || array.isEmpty()) {
                continue;
            }
            readArray(accumulator, entry.getKey(), array);
        }

        CanonicalModel model = accumulator.build();
        log.info("Parsed JSON EA dataset: {} applications, {} relationships, {} interfaces, "
                        + "{} information objects, {} business processes, {} process mappings, "
                        + "{} ownership records, {} declared gaps",
                model.applications().size(), model.relationships().size(), model.interfaces().size(),
                model.informationObjects().size(), model.businessProcesses().size(),
                model.processMappings().size(), model.applicationOwnerships().size(),
                model.dataQualityGaps().size());
        return model;
    }

    private void readArray(IngestionSupport.Accumulator accumulator, String arrayName, JsonNode array) {
        if (IngestionSupport.isSkipped(properties, arrayName)) {
            log.debug("Skipping configured non-entity JSON array '{}'", arrayName);
            return;
        }

        List<String> headers = signature(array);
        if (headers.isEmpty()) {
            return;
        }

        List<IngestionSupport.TableBinding> bindings = resolveBindings(arrayName, headers);
        if (bindings.isEmpty()) {
            log.warn("JSON array '{}' did not match any known entity; skipping", arrayName);
            accumulator.note("JSON array '" + arrayName + "' did not match any known entity and was skipped");
            return;
        }
        bindings.forEach(binding -> binding.notes().forEach(accumulator::note));

        for (JsonNode node : array) {
            if (node == null || !node.isObject()) {
                continue;
            }
            for (IngestionSupport.TableBinding binding : bindings) {
                accumulator.add(binding.entity(), IngestionSupport.reader(binding, column -> {
                    JsonNode value = node.get(column);
                    return (value == null || value.isNull()) ? null : value.asText();
                }));
            }
        }
        log.debug("Bound JSON array '{}' to entit{} '{}'",
                arrayName, bindings.size() == 1 ? "y" : "ies", bindings.stream()
                        .map(IngestionSupport.TableBinding::entity).toList());
    }

    /**
     * Resolves every entity type an array should be read as: entities
     * explicitly named to this array in configuration (a mapping array may name
     * more than one), otherwise the single best heuristic match.
     */
    private List<IngestionSupport.TableBinding> resolveBindings(String arrayName, List<String> headers) {
        List<String> explicit = IngestionSupport.entitiesByTableName(properties, arrayName);
        List<IngestionSupport.TableBinding> bindings = new ArrayList<>();
        if (!explicit.isEmpty()) {
            for (String entity : explicit) {
                IngestionSupport.TableBinding binding = IngestionSupport.bindEntity(properties, entity, headers);
                if (binding != null) {
                    bindings.add(binding);
                }
            }
        } else {
            IngestionSupport.TableBinding binding = IngestionSupport.bind(properties, arrayName, headers);
            if (binding != null) {
                bindings.add(binding);
            }
        }
        return bindings;
    }

    /** Union of property names across a bounded sample of the array's objects. */
    private List<String> signature(JsonNode array) {
        int limit = properties.getMatching().getSignatureSampleSize();
        Set<String> headers = new LinkedHashSet<>();
        int seen = 0;
        for (JsonNode node : array) {
            if (node == null || !node.isObject()) {
                continue;
            }
            node.fieldNames().forEachRemaining(headers::add);
            if (++seen >= limit) {
                break;
            }
        }
        return new ArrayList<>(headers);
    }
}
