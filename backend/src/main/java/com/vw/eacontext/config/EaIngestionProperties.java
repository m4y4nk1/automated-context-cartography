package com.vw.eacontext.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Externalized, format-independent configuration for EA ingestion.
 *
 * <p>Bound from {@code ea.ingestion.*} in {@code application.yml}. The
 * {@link #fields column mapping} is shared by every parser (JSON, CSV, Excel),
 * while each format contributes only its own locator config (JSON array names,
 * Excel sheet names, CSV file names). Renaming a source column/field/sheet is a
 * pure configuration change.</p>
 *
 * <p>{@link #aliases} and {@link #matching} extend this into a fully
 * schema-agnostic resolver: an arbitrary EA-like workbook is bound by header
 * signature rather than by a fixed sheet name.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ea.ingestion")
public class EaIngestionProperties {

    /**
     * Shared per-entity field mapping: outer key is the entity type
     * (e.g. {@code application}), inner map is {@code modelField -> sourceColumn}.
     * The source column is the JSON property, CSV header, or Excel column header.
     */
    private Map<String, Map<String, String>> fields = new LinkedHashMap<>();

    /**
     * Per-entity, per-model-field accepted source-column aliases:
     * {@code entity -> modelField -> [alias, ...]}. Matching is normalized
     * (case/whitespace/underscore/camelCase-insensitive).
     */
    private Map<String, Map<String, List<String>>> aliases = new LinkedHashMap<>();

    /**
     * Per-entity list of model fields the dataset report marks as required,
     * beyond {@code id}/{@code name} (which already get their own fallback +
     * note in {@code bindTo} when no column matches at all). Used only to
     * detect a required column that's entirely absent from a sheet's headers —
     * distinct from, and reported separately from, a value that's merely blank
     * on some rows once the column exists (see {@code ValidationService}'s
     * per-row required-field checks for that).
     */
    private Map<String, List<String>> requiredFields = new LinkedHashMap<>();

    /**
     * Entities that can share one physical table: {@code entity -> [companion, ...]}.
     * When a table is recognized by its headers as {@code entity}, each companion
     * whose own header signature also clears {@link Matching#getMinConfidence()}
     * is bound from the same rows (see {@code IngestionSupport.resolveBindings}).
     */
    private Map<String, List<String>> companionEntities = new LinkedHashMap<>();

    /** Heuristic entity-detection tuning. */
    private Matching matching = new Matching();

    /** JSON-specific configuration. */
    private Json json = new Json();

    /** Excel-specific configuration. */
    private Excel excel = new Excel();

    /** CSV-specific configuration. */
    private Csv csv = new Csv();

    /**
     * Resolves the source column/field name for a canonical model field,
     * falling back to the model field name when no explicit mapping exists.
     *
     * @param entity     entity type key (e.g. {@code application})
     * @param modelField canonical model field name (e.g. {@code lifecycleStatus})
     * @return the source column/field name to read
     */
    public String column(String entity, String modelField) {
        return fields.getOrDefault(entity, Map.of()).getOrDefault(modelField, modelField);
    }

    /**
     * All accepted source-column candidates for a model field, in priority
     * order: the explicit {@code fields} mapping, then configured aliases, then
     * the model field name itself.
     */
    public List<String> columnCandidates(String entity, String modelField) {
        List<String> candidates = new ArrayList<>();
        String explicit = fields.getOrDefault(entity, Map.of()).get(modelField);
        if (explicit != null && !explicit.isBlank()) {
            candidates.add(explicit);
        }
        List<String> configured = aliases.getOrDefault(entity, Map.of()).get(modelField);
        if (configured != null) {
            candidates.addAll(configured);
        }
        candidates.add(modelField);
        return candidates;
    }

    /** The model fields configured for an entity (mapping keys plus alias keys). */
    public List<String> modelFields(String entity) {
        List<String> result = new ArrayList<>(fields.getOrDefault(entity, Map.of()).keySet());
        for (String field : aliases.getOrDefault(entity, Map.of()).keySet()) {
            if (!result.contains(field)) {
                result.add(field);
            }
        }
        return result;
    }

    /** Every entity type known to the configuration. */
    public List<String> entityTypes() {
        List<String> types = new ArrayList<>(fields.keySet());
        for (String type : aliases.keySet()) {
            if (!types.contains(type)) {
                types.add(type);
            }
        }
        return types;
    }

    /** Configured JSON array name for an entity. */
    public String jsonArrayName(String entity) {
        return json.getArrays().get(entity);
    }

    /** Model fields configured as required for an entity (see {@link #requiredFields}). */
    public List<String> requiredFields(String entity) {
        return requiredFields.getOrDefault(entity, List.of());
    }

    /** Entities configured to share a table with {@code entity} (see {@link #companionEntities}). */
    public List<String> companionEntities(String entity) {
        return companionEntities.getOrDefault(entity, List.of());
    }

    /** Heuristic matching thresholds for schema-agnostic table detection. */
    @Getter
    @Setter
    public static class Matching {
        /** Minimum normalized signature score required to bind a table to an entity. */
        private double minConfidence = 0.5;

        /** How many leading rows are scanned when auto-detecting the header row. */
        private int headerScanRows = 25;

        /** How many records are sampled to derive a JSON array's header signature. */
        private int signatureSampleSize = 25;

        /** Table names (sheet/file/array) skipped outright, e.g. README metadata. */
        private List<String> skipTables = new ArrayList<>();
    }

    /** JSON parser configuration. */
    @Getter
    @Setter
    public static class Json {
        /** Entity type -> top-level JSON array name. */
        private Map<String, String> arrays = new LinkedHashMap<>();
    }

    /** Excel parser configuration. */
    @Getter
    @Setter
    public static class Excel {
        /** Entity type -> worksheet name. */
        private Map<String, String> sheets = new LinkedHashMap<>();
    }

    /** CSV parser configuration. */
    @Getter
    @Setter
    public static class Csv {
        /** Entity type -> file name (used when reading a ZIP of CSV files). */
        private Map<String, String> files = new LinkedHashMap<>();
    }
}
