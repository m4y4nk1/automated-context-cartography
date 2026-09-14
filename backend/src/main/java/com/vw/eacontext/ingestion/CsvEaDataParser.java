package com.vw.eacontext.ingestion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import com.vw.eacontext.config.EaIngestionProperties;
import com.vw.eacontext.exception.EaIngestionException;
import com.vw.eacontext.model.CanonicalModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link EaDataParser} for CSV sources, backed by Apache Commons CSV, using one
 * CSV file per entity (a mapping-style file such as Auriga's
 * {@code business_processes.csv} can bind to more than one entity — see
 * {@link IngestionSupport#entitiesByTableName}).
 *
 * <p>Two entry points are offered:</p>
 * <ul>
 *   <li>{@link #parse(Map)} — the natural API: a map of entity type to the
 *       corresponding CSV stream.</li>
 *   <li>{@link #parse(InputStream)} — the {@link EaDataParser} contract, reading
 *       a ZIP archive whose entries are the per-entity CSV files. Entry names are
 *       matched via {@code ea.ingestion.csv.files}; unrecognized entries fall back
 *       to header-signature detection so arbitrary CSV bundles still ingest.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsvEaDataParser implements EaDataParser {

    private final EaIngestionProperties properties;

    /**
     * Parses a set of per-entity CSV streams into the canonical model.
     *
     * @param csvByEntity map of entity type key (see {@link IngestionSupport})
     *                    to its CSV {@link InputStream}; missing entities yield
     *                    empty lists
     * @return the populated canonical model
     */
    public CanonicalModel parse(Map<String, InputStream> csvByEntity) {
        IngestionSupport.Accumulator accumulator = new IngestionSupport.Accumulator();
        csvByEntity.forEach((entity, in) -> readCsv(accumulator, List.of(entity), null, in));
        return logged(accumulator.build());
    }

    /**
     * Reads a ZIP archive of per-entity CSV files. Entry names are matched to
     * entity types via {@code ea.ingestion.csv.files} (basename, case-insensitive),
     * falling back to header-signature detection.
     */
    @Override
    public CanonicalModel parse(InputStream in) {
        IngestionSupport.Accumulator accumulator = new IngestionSupport.Accumulator();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String table = stripExtension(baseName(entry.getName()));
                if (IngestionSupport.isSkipped(properties, table)) {
                    continue;
                }
                List<String> explicit = IngestionSupport.entitiesByTableName(properties, table);
                readCsv(accumulator, explicit, table, new ByteArrayInputStream(readAll(zip)));
            }
        } catch (IOException e) {
            throw new EaIngestionException("Failed to read CSV ZIP archive", e);
        }
        if (accumulator.isEmpty()) {
            throw new EaIngestionException("CSV ZIP archive contained no recognized entity files");
        }
        return logged(accumulator.build());
    }

    /**
     * Reads a single CSV stream. When {@code explicitEntities} is non-empty the
     * binding is forced to those entities (all fed from the same rows, for
     * mapping-style tables); otherwise the entity is resolved from the table
     * name and header signature.
     */
    private void readCsv(IngestionSupport.Accumulator accumulator, List<String> explicitEntities,
                          String tableName, InputStream in) {
        if (in == null) {
            log.warn("No CSV provided for entit{} '{}'; treating as empty",
                    explicitEntities.size() == 1 ? "y" : "ies", explicitEntities);
            return;
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();

        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            List<String> headers = parser.getHeaderNames();
            List<IngestionSupport.TableBinding> bindings = new ArrayList<>();
            if (explicitEntities != null && !explicitEntities.isEmpty()) {
                for (String entity : explicitEntities) {
                    IngestionSupport.TableBinding binding = IngestionSupport.bindEntity(properties, entity, headers);
                    if (binding != null) {
                        bindings.add(binding);
                    }
                }
            } else {
                IngestionSupport.TableBinding binding = IngestionSupport.bind(properties, tableName, headers);
                if (binding != null) {
                    bindings.add(binding);
                }
            }

            if (bindings.isEmpty()) {
                String label = tableName != null ? tableName : String.valueOf(explicitEntities);
                log.warn("CSV '{}' did not match any known entity; skipping", label);
                accumulator.note("CSV '" + label + "' did not match any known entity and was skipped");
                return;
            }
            // Only noted once the file actually contributes data. commons-csv
            // resolves a duplicate header name to its LAST occurrence (the
            // opposite tie-break from Excel's first-wins) — not changed here,
            // only surfaced.
            String csvLabel = tableName != null ? tableName : String.valueOf(explicitEntities);
            for (String duplicate : duplicateHeaders(headers)) {
                accumulator.note("CSV '" + csvLabel + "' has more than one column named '" + duplicate
                        + "'; only the last is used, the rest are ignored");
            }
            bindings.forEach(binding -> binding.notes().forEach(accumulator::note));

            for (CSVRecord row : parser) {
                for (IngestionSupport.TableBinding binding : bindings) {
                    accumulator.add(binding.entity(), IngestionSupport.reader(binding, column ->
                            (row.isMapped(column) && row.isSet(column)) ? row.get(column) : null));
                }
            }
        } catch (IOException e) {
            throw new EaIngestionException(
                    "Failed to read CSV for '" + (tableName != null ? tableName : explicitEntities) + "'", e);
        }
    }

    private CanonicalModel logged(CanonicalModel model) {
        log.info("Parsed CSV EA dataset: {} applications, {} relationships, {} interfaces, "
                        + "{} information objects, {} business processes, {} process mappings, "
                        + "{} ownership records, {} declared gaps",
                model.applications().size(), model.relationships().size(), model.interfaces().size(),
                model.informationObjects().size(), model.businessProcesses().size(),
                model.processMappings().size(), model.applicationOwnerships().size(),
                model.dataQualityGaps().size());
        return model;
    }

    /** Header text (normalized) that appears more than once, in first-seen order, no duplicates in the result. */
    private static List<String> duplicateHeaders(List<String> headers) {
        Set<String> seenNormalized = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String header : headers) {
            if (!seenNormalized.add(IngestionSupport.normalize(header)) && !duplicates.contains(header)) {
                duplicates.add(header);
            }
        }
        return duplicates;
    }

    private static String baseName(String path) {
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
