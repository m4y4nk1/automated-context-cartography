package com.vw.eacontext.ingestion;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.vw.eacontext.config.EaIngestionProperties;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.ApplicationOwnership;
import com.vw.eacontext.model.BusinessCriticality;
import com.vw.eacontext.model.BusinessProcess;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Classification;
import com.vw.eacontext.model.DataFormat;
import com.vw.eacontext.model.DataQualityGap;
import com.vw.eacontext.model.DependencyCriticality;
import com.vw.eacontext.model.Frequency;
import com.vw.eacontext.model.Hosting;
import com.vw.eacontext.model.InformationObject;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.model.LifecycleStatus;
import com.vw.eacontext.model.Operation;
import com.vw.eacontext.model.ProcessCriticality;
import com.vw.eacontext.model.ProcessMapping;
import com.vw.eacontext.model.Protocol;
import com.vw.eacontext.model.Relationship;
import com.vw.eacontext.model.RelationshipType;
import com.vw.eacontext.model.RoleOfApplication;
import com.vw.eacontext.model.VendorType;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared, format-independent helpers for ingestion parsers.
 *
 * <p>Provides the canonical entity-type keys used in the configuration mapping,
 * value/enum normalization, builders that assemble domain entities from a
 * {@link RowReader}, and the schema-agnostic table resolver
 * ({@link #bind(EaIngestionProperties, String, List)}) that lets the JSON, CSV
 * and Excel parsers ingest arbitrary EA-shaped sources without hardcoded sheet
 * or column names.</p>
 */
@Slf4j
public final class IngestionSupport {

    /** Entity type keys — must match the keys used under {@code ea.ingestion.fields}. */
    public static final String APPLICATION = "application";
    public static final String RELATIONSHIP = "relationship";
    public static final String INTERFACE = "interface";
    public static final String INFORMATION_OBJECT = "informationObject";
    public static final String BUSINESS_PROCESS = "businessProcess";
    public static final String PROCESS_MAPPING = "processMapping";
    public static final String APPLICATION_OWNERSHIP = "applicationOwnership";
    public static final String DATA_QUALITY_GAP = "dataQualityGap";

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));

    private IngestionSupport() {
    }

    // --- Value normalization --------------------------------------------------

    /** Returns {@code null} for null/blank input, otherwise the trimmed value. */
    public static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Normalizes an identifier for tolerant matching: lower-cased with every
     * non-alphanumeric character removed, so {@code "Application Id"},
     * {@code "application_id"} and {@code "applicationId"} collapse to the same
     * token.
     */
    public static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Parses a lifecycle status by label; an unrecognized but present label is
     * logged as a warning and left unset ({@code null}) rather than thrown.
     */
    public static LifecycleStatus lifecycleStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        LifecycleStatus resolved = LifecycleStatus.fromLabel(raw);
        if (resolved == null) {
            log.warn("Unknown lifecycleStatus '{}'; leaving unset", raw);
        }
        return resolved;
    }

    /**
     * Generic enum-safe parser: matches {@code raw} against an enum's constant
     * names after normalizing both sides (case/space/punctuation-insensitive),
     * e.g. {@code "REST/HTTPS"} -&gt; {@code Protocol.REST_HTTPS} and
     * {@code "End of Life"} -&gt; any enum's {@code END_OF_LIFE} constant.
     * Unrecognized-but-present values are logged as a warning and resolve to
     * {@code fallback} (commonly {@code null}) rather than throwing.
     */
    public static <T extends Enum<T>> T enumFromLabel(Class<T> type, String raw, T fallback) {
        String value = blankToNull(raw);
        if (value == null) {
            return fallback;
        }
        String key = normalize(value);
        for (T constant : type.getEnumConstants()) {
            if (normalize(constant.name()).equals(key)) {
                return constant;
            }
        }
        log.warn("Unknown {} value '{}'; {}", type.getSimpleName(), raw,
                fallback == null ? "leaving unset" : "defaulting to " + fallback);
        return fallback;
    }

    /** {@link #enumFromLabel(Class, String, Enum)} with a {@code null} fallback. */
    public static <T extends Enum<T>> T enumFromLabel(Class<T> type, String raw) {
        return enumFromLabel(type, raw, null);
    }

    /**
     * Parses a date carried as a plain source string (ISO first, then a small
     * set of common spreadsheet renderings). Never throws — an unparseable
     * value is logged as a warning and resolves to {@code null}.
     */
    public static LocalDate parseIsoDate(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next candidate format
            }
        }
        log.warn("Unparseable date '{}'; leaving unset", raw);
        return null;
    }

    // --- Foreign-key resolution ------------------------------------------------

    /**
     * The result of resolving a raw foreign-key value against the set of known
     * ids for its target entity type — the single shared building block for
     * every "broken reference" / "ghost id" check across validation and the
     * insight detectors.
     *
     * @param id      the trimmed raw id, or {@code null} when blank
     * @param present {@code true} when {@code id} is non-blank and known
     * @param ghost   {@code true} when {@code id} is non-blank but NOT known
     *                (a reference to a record that doesn't exist)
     */
    public record ReferenceCheck(String id, boolean present, boolean ghost) {
    }

    /** Resolves a raw foreign-key value against a target entity's known id set. */
    public static ReferenceCheck resolveReference(String rawId, Set<String> knownIds) {
        String id = blankToNull(rawId);
        if (id == null) {
            return new ReferenceCheck(null, false, false);
        }
        boolean present = knownIds.contains(id);
        return new ReferenceCheck(id, present, !present);
    }

    // --- Schema-agnostic table resolution -------------------------------------

    /**
     * The result of binding one source table (Excel sheet, CSV file or JSON
     * array) to a canonical entity type.
     *
     * @param entity        the resolved entity type key
     * @param confidence    signature score in {@code [0,1]}; {@code 1} for a name hit
     * @param fieldToColumn canonical model field -> actual source column name
     * @param extraColumns  source columns not bound to any model field
     * @param notes         non-blocking diagnostics raised while binding
     */
    public record TableBinding(
            String entity,
            double confidence,
            Map<String, String> fieldToColumn,
            List<String> extraColumns,
            List<String> notes) {
    }

    /** Reads one source row, translated from canonical model fields. */
    public interface RowReader {
        /** @return the trimmed value for a canonical model field, or {@code null}. */
        String value(String modelField);

        /** @return unrecognized source columns and their values (never {@code null}). */
        Map<String, String> extras();
    }

    /** @return {@code true} when the table name is configured to be skipped outright. */
    public static boolean isSkipped(EaIngestionProperties properties, String tableName) {
        String key = normalize(tableName);
        for (String skip : properties.getMatching().getSkipTables()) {
            if (normalize(skip).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Binds a source table to the best-matching entity type.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>an exact (normalized) table-name hit against the configured
     *       sheet/file/array name — deterministic, preserving legacy behaviour;</li>
     *   <li>otherwise a header-signature score against every configured entity,
     *       accepted when it clears {@code ea.ingestion.matching.min-confidence}.</li>
     * </ol>
     *
     * @return the binding, or {@code null} when nothing matched confidently
     */
    public static TableBinding bind(EaIngestionProperties properties, String tableName, List<String> headers) {
        List<String> cleanHeaders = new ArrayList<>();
        for (String header : headers) {
            String trimmed = blankToNull(header);
            if (trimmed != null) {
                cleanHeaders.add(trimmed);
            }
        }
        if (cleanHeaders.isEmpty()) {
            return null;
        }

        String named = entityByTableName(properties, tableName);
        if (named != null) {
            return bindTo(properties, named, cleanHeaders, 1.0);
        }

        String best = null;
        double bestScore = 0.0;
        for (String entity : properties.entityTypes()) {
            double score = signatureScore(properties, entity, cleanHeaders);
            if (score > bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        if (best == null || bestScore < properties.getMatching().getMinConfidence()) {
            return null;
        }
        return bindTo(properties, best, cleanHeaders, bestScore);
    }

    /**
     * Binds headers to an entity type that the caller already knows (e.g. a CSV
     * stream supplied under an explicit entity key, or one of several entities
     * that a single source table fans out into — see
     * {@link #entitiesByTableName}).
     */
    public static TableBinding bindEntity(EaIngestionProperties properties, String entity, List<String> headers) {
        List<String> cleanHeaders = new ArrayList<>();
        for (String header : headers) {
            String trimmed = blankToNull(header);
            if (trimmed != null) {
                cleanHeaders.add(trimmed);
            }
        }
        return cleanHeaders.isEmpty() ? null : bindTo(properties, entity, cleanHeaders, 1.0);
    }

    /**
     * Every entity type explicitly configured (by exact, normalized name) for a
     * source table. Most tables map to exactly one entity; a mapping-style
     * table (e.g. Auriga's {@code BusinessProcesses}, which is simultaneously a
     * {@code businessProcess} record and a {@code processMapping} row) can map
     * to more than one — callers should feed every row through each entry.
     * Empty when nothing is explicitly configured for this name (callers then
     * fall back to heuristic {@link #bind}).
     */
    public static List<String> entitiesByTableName(EaIngestionProperties properties, String tableName) {
        return matchingEntitiesByTableName(properties, tableName);
    }

    /** Exact, normalized table-name match against the configured Excel/CSV/JSON hints. */
    private static String entityByTableName(EaIngestionProperties properties, String tableName) {
        List<String> matches = matchingEntitiesByTableName(properties, tableName);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static List<String> matchingEntitiesByTableName(EaIngestionProperties properties, String tableName) {
        String key = normalize(tableName);
        if (key.isEmpty()) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.getExcel().getSheets().entrySet()) {
            if (normalize(entry.getValue()).equals(key) && !matches.contains(entry.getKey())) {
                matches.add(entry.getKey());
            }
        }
        for (Map.Entry<String, String> entry : properties.getCsv().getFiles().entrySet()) {
            if (normalize(stripExtension(entry.getValue())).equals(key) && !matches.contains(entry.getKey())) {
                matches.add(entry.getKey());
            }
        }
        for (String entity : properties.entityTypes()) {
            String arrayName = properties.jsonArrayName(entity);
            if (arrayName != null && normalize(arrayName).equals(key) && !matches.contains(entity)) {
                matches.add(entity);
            }
        }
        return matches;
    }

    /**
     * Fraction of the entity's configured model fields that a header set can
     * satisfy, lightly penalized when the table carries far more unrelated
     * columns than matched ones.
     */
    private static double signatureScore(EaIngestionProperties properties, String entity, List<String> headers) {
        List<String> modelFields = properties.modelFields(entity);
        if (modelFields.isEmpty()) {
            return 0.0;
        }
        Set<String> normalizedHeaders = new LinkedHashSet<>();
        for (String header : headers) {
            normalizedHeaders.add(normalize(header));
        }

        int matched = 0;
        Set<String> matchedColumns = new LinkedHashSet<>();
        for (String field : modelFields) {
            String column = resolveColumn(properties, entity, field, normalizedHeaders, headers);
            if (column != null) {
                matched++;
                matchedColumns.add(normalize(column));
            }
        }
        double fieldCoverage = (double) matched / modelFields.size();
        double headerCoverage = (double) matchedColumns.size() / normalizedHeaders.size();
        // Field coverage dominates; header coverage breaks ties between entities
        // whose field sets overlap.
        return (fieldCoverage * 0.8) + (headerCoverage * 0.2);
    }

    private static TableBinding bindTo(EaIngestionProperties properties, String entity,
                                       List<String> headers, double confidence) {
        Set<String> normalizedHeaders = new LinkedHashSet<>();
        for (String header : headers) {
            normalizedHeaders.add(normalize(header));
        }

        Map<String, String> fieldToColumn = new LinkedHashMap<>();
        Set<String> boundColumns = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();

        for (String field : properties.modelFields(entity)) {
            String column = resolveColumn(properties, entity, field, normalizedHeaders, headers);
            if (column != null) {
                fieldToColumn.put(field, column);
                boundColumns.add(normalize(column));
            }
        }

        // Fallback so arbitrary files remain ingestible: first column is the id,
        // first remaining column is the name.
        if (!fieldToColumn.containsKey("id")) {
            fieldToColumn.put("id", headers.get(0));
            boundColumns.add(normalize(headers.get(0)));
            notes.add("No id column matched for entity '" + entity + "'; using first column '"
                    + headers.get(0) + "'");
        }
        if (!fieldToColumn.containsKey("name") && properties.modelFields(entity).contains("name")) {
            for (String header : headers) {
                if (!boundColumns.contains(normalize(header))) {
                    fieldToColumn.put("name", header);
                    boundColumns.add(normalize(header));
                    notes.add("No name column matched for entity '" + entity + "'; using column '"
                            + header + "'");
                    break;
                }
            }
        }

        List<String> extras = new ArrayList<>();
        for (String header : headers) {
            if (!boundColumns.contains(normalize(header))) {
                extras.add(header);
            }
        }
        return new TableBinding(entity, confidence, fieldToColumn, extras, notes);
    }

    /** Finds the actual header matching any configured candidate for a model field. */
    private static String resolveColumn(EaIngestionProperties properties, String entity, String modelField,
                                        Set<String> normalizedHeaders, List<String> headers) {
        for (String candidate : properties.columnCandidates(entity, modelField)) {
            String normalizedCandidate = normalize(candidate);
            if (normalizedCandidate.isEmpty() || !normalizedHeaders.contains(normalizedCandidate)) {
                continue;
            }
            for (String header : headers) {
                if (normalize(header).equals(normalizedCandidate)) {
                    return header;
                }
            }
        }
        return null;
    }

    /** Wraps a raw {@code column -> value} accessor as a canonical {@link RowReader}. */
    public static RowReader reader(TableBinding binding, Function<String, String> rawByColumn) {
        return new RowReader() {
            @Override
            public String value(String modelField) {
                String column = binding.fieldToColumn().get(modelField);
                return column == null ? null : blankToNull(rawByColumn.apply(column));
            }

            @Override
            public Map<String, String> extras() {
                Map<String, String> extras = new LinkedHashMap<>();
                for (String column : binding.extraColumns()) {
                    String value = blankToNull(rawByColumn.apply(column));
                    if (value != null) {
                        extras.put(column, value);
                    }
                }
                return extras;
            }
        };
    }

    private static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    // --- Format-agnostic entity builders --------------------------------------

    public static Application toApplication(RowReader read) {
        return Application.builder()
                .id(read.value("id"))
                .name(read.value("name"))
                .description(read.value("description"))
                .businessDomain(read.value("businessDomain"))
                .businessCriticality(enumFromLabel(BusinessCriticality.class, read.value("businessCriticality")))
                .lifecycleStatus(lifecycleStatus(read.value("lifecycleStatus")))
                .lifecycleStartDate(parseIsoDate(read.value("lifecycleStartDate")))
                .lifecycleEndDate(parseIsoDate(read.value("lifecycleEndDate")))
                .hosting(enumFromLabel(Hosting.class, read.value("hosting")))
                .vendorType(enumFromLabel(VendorType.class, read.value("vendorType")))
                .ownerEmployeeId(read.value("ownerEmployeeId"))
                .costCenter(read.value("costCenter"))
                .attributes(read.extras())
                .build();
    }

    public static Relationship toRelationship(RowReader read) {
        return Relationship.builder()
                .id(read.value("id"))
                .sourceApplicationId(read.value("sourceApplicationId"))
                .relationshipType(enumFromLabel(RelationshipType.class, read.value("relationshipType")))
                .targetApplicationId(read.value("targetApplicationId"))
                .dependencyCriticality(enumFromLabel(DependencyCriticality.class, read.value("dependencyCriticality")))
                .attributes(read.extras())
                .build();
    }

    public static Interface toInterface(RowReader read) {
        return Interface.builder()
                .id(read.value("id"))
                .name(read.value("name"))
                .providerApplicationId(read.value("providerApplicationId"))
                .consumerApplicationId(read.value("consumerApplicationId"))
                .protocol(enumFromLabel(Protocol.class, read.value("protocol")))
                .dataFormat(enumFromLabel(DataFormat.class, read.value("dataFormat")))
                .frequency(enumFromLabel(Frequency.class, read.value("frequency")))
                .interfaceStatus(enumFromLabel(InterfaceStatus.class, read.value("interfaceStatus")))
                .attributes(read.extras())
                .build();
    }

    public static InformationObject toInformationObject(RowReader read) {
        return InformationObject.builder()
                .id(read.value("id"))
                .informationObject(read.value("informationObject"))
                .classification(enumFromLabel(Classification.class, read.value("classification")))
                .sourceApplicationId(read.value("sourceApplicationId"))
                .targetApplicationId(read.value("targetApplicationId"))
                .operation(enumFromLabel(Operation.class, read.value("operation")))
                .interfaceId(read.value("interfaceId"))
                .attributes(read.extras())
                .build();
    }

    public static BusinessProcess toBusinessProcess(RowReader read) {
        return BusinessProcess.builder()
                .id(read.value("id"))
                .name(read.value("name"))
                .processDomain(read.value("processDomain"))
                .attributes(read.extras())
                .build();
    }

    public static ProcessMapping toProcessMapping(RowReader read) {
        return ProcessMapping.builder()
                .id(read.value("id"))
                .businessProcessId(read.value("businessProcessId"))
                .supportingApplicationId(read.value("supportingApplicationId"))
                .roleOfApplication(enumFromLabel(RoleOfApplication.class, read.value("roleOfApplication")))
                .processCriticality(enumFromLabel(ProcessCriticality.class, read.value("processCriticality")))
                .attributes(read.extras())
                .build();
    }

    public static ApplicationOwnership toApplicationOwnership(RowReader read) {
        return ApplicationOwnership.builder()
                .id(read.value("id"))
                .applicationId(read.value("applicationId"))
                .applicationOwner(read.value("applicationOwner"))
                .ownerEmployeeId(read.value("ownerEmployeeId"))
                .systemCustodian(read.value("systemCustodian"))
                .businessOwner(read.value("businessOwner"))
                .supportGroup(read.value("supportGroup"))
                .department(read.value("department"))
                .attributes(read.extras())
                .build();
    }

    public static DataQualityGap toDataQualityGap(RowReader read) {
        return DataQualityGap.builder()
                .id(read.value("id"))
                .gapType(read.value("gapType"))
                .entityType(read.value("entityType"))
                .entityId(read.value("entityId"))
                .relatedApplicationId(read.value("relatedApplicationId"))
                .description(read.value("description"))
                .severity(enumFromLabel(DependencyCriticality.class, read.value("severity")))
                .attributes(read.extras())
                .build();
    }

    /**
     * Accumulates rows of any entity type into a {@link CanonicalModel}.
     * {@code businessProcess} rows are de-duplicated by id, since Auriga's
     * {@code BusinessProcesses} table is a mapping sheet where the process
     * itself repeats once per supporting application.
     */
    public static final class Accumulator {

        private final List<Application> applications = new ArrayList<>();
        private final List<Relationship> relationships = new ArrayList<>();
        private final List<Interface> interfaces = new ArrayList<>();
        private final List<InformationObject> informationObjects = new ArrayList<>();
        private final List<BusinessProcess> businessProcesses = new ArrayList<>();
        private final List<ProcessMapping> processMappings = new ArrayList<>();
        private final List<ApplicationOwnership> applicationOwnerships = new ArrayList<>();
        private final List<DataQualityGap> dataQualityGaps = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private final Set<String> businessProcessIds = new LinkedHashSet<>();

        public void add(String entity, RowReader read) {
            switch (entity) {
                case APPLICATION -> applications.add(toApplication(read));
                case RELATIONSHIP -> relationships.add(toRelationship(read));
                case INTERFACE -> interfaces.add(toInterface(read));
                case INFORMATION_OBJECT -> informationObjects.add(toInformationObject(read));
                case BUSINESS_PROCESS -> addBusinessProcess(toBusinessProcess(read));
                case PROCESS_MAPPING -> processMappings.add(toProcessMapping(read));
                case APPLICATION_OWNERSHIP -> applicationOwnerships.add(toApplicationOwnership(read));
                case DATA_QUALITY_GAP -> dataQualityGaps.add(toDataQualityGap(read));
                default -> log.warn("No builder registered for entity '{}'; row skipped", entity);
            }
        }

        private void addBusinessProcess(BusinessProcess process) {
            if (process.id() == null || businessProcessIds.add(process.id())) {
                businessProcesses.add(process);
            }
        }

        public void note(String note) {
            notes.add(note);
        }

        public boolean isEmpty() {
            return applications.isEmpty() && relationships.isEmpty() && interfaces.isEmpty()
                    && informationObjects.isEmpty() && businessProcesses.isEmpty() && processMappings.isEmpty()
                    && applicationOwnerships.isEmpty() && dataQualityGaps.isEmpty();
        }

        public CanonicalModel build() {
            return CanonicalModel.builder()
                    .applications(applications)
                    .relationships(relationships)
                    .interfaces(interfaces)
                    .informationObjects(informationObjects)
                    .businessProcesses(businessProcesses)
                    .processMappings(processMappings)
                    .applicationOwnerships(applicationOwnerships)
                    .dataQualityGaps(dataQualityGaps)
                    .ingestionNotes(notes)
                    .build();
        }
    }
}
