package com.vw.eacontext.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.vw.eacontext.config.EaIngestionProperties;
import com.vw.eacontext.exception.EaIngestionException;
import com.vw.eacontext.model.CanonicalModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link EaDataParser} for Excel (.xlsx) workbooks, backed by Apache POI.
 *
 * <p>Every worksheet is bound to one or more entity types by
 * {@link IngestionSupport#entitiesByTableName}/{@link IngestionSupport#bind}:
 * first by its configured sheet name (a mapping-style sheet such as Auriga's
 * {@code BusinessProcesses} can bind to more than one entity), otherwise by
 * header signature. Rows are streamed straight into the accumulator (no
 * intermediate per-sheet lists), the header row is auto-detected as the row
 * with the most recognizable columns, and unmatched sheets are skipped with a
 * non-blocking note.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelEaDataParser implements EaDataParser {

    private final EaIngestionProperties properties;
    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public CanonicalModel parse(InputStream in) {
        try (Workbook workbook = openWorkbook(in)) {
            IngestionSupport.Accumulator accumulator = new IngestionSupport.Accumulator();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                readSheet(accumulator, workbook.getSheetAt(i));
            }

            CanonicalModel model = accumulator.build();
            log.info("Parsed Excel EA dataset: {} applications, {} relationships, {} interfaces, "
                            + "{} information objects, {} business processes, {} process mappings, "
                            + "{} ownership records, {} declared gaps",
                    model.applications().size(), model.relationships().size(), model.interfaces().size(),
                    model.informationObjects().size(), model.businessProcesses().size(),
                    model.processMappings().size(), model.applicationOwnerships().size(),
                    model.dataQualityGaps().size());
            return model;
        } catch (IOException e) {
            throw new EaIngestionException("Failed to read Excel EA dataset", e);
        }
    }

    /**
     * Opens the workbook, translating POI's format-detection failures into one
     * consistent {@link EaIngestionException} instead of leaking POI's internal
     * exception types/messages.
     *
     * <p>POI signals "this isn't a valid .xlsx" via several different unchecked
     * exception types depending on exactly how the content fails format
     * detection — {@code OLE2NotOfficeXmlFileException} and
     * {@code NotOfficeXmlFileException} (both {@code IllegalArgumentException}
     * subtypes, e.g. a legacy {@code .xls} or Office-2003 XML file renamed
     * {@code .xlsx}) and {@code POIXMLException} (a plain
     * {@code RuntimeException}, e.g. an arbitrary ZIP renamed {@code .xlsx}
     * with no OOXML content-types part). None of these extend
     * {@code IOException}, so a plain {@code catch (IOException)} around the
     * constructor lets them escape unwrapped — inconsistently, as either a
     * generic 400 or an unhandled-exception 500 depending on which one — with
     * POI's raw internal message reaching the caller instead of this parser's
     * own. Scoped to just this one call so a genuine bug elsewhere in parsing
     * still surfaces as a real, diagnosable error rather than being masked.
     */
    private Workbook openWorkbook(InputStream in) {
        try {
            return new XSSFWorkbook(in);
        } catch (IOException e) {
            throw new EaIngestionException("Failed to read Excel EA dataset", e);
        } catch (RuntimeException e) {
            throw new EaIngestionException(
                    "Failed to read Excel EA dataset — the file doesn't look like a valid .xlsx workbook", e);
        }
    }

    private void readSheet(IngestionSupport.Accumulator accumulator, Sheet sheet) {
        String sheetName = sheet.getSheetName();
        if (IngestionSupport.isSkipped(properties, sheetName)) {
            log.debug("Skipping configured non-entity sheet '{}'", sheetName);
            return;
        }

        HeaderRow header = findHeader(sheet, sheetName);
        if (header == null) {
            log.warn("No header row found in sheet '{}'; skipping", sheetName);
            accumulator.note("Sheet '" + sheetName + "' has no recognizable header row and was skipped");
            return;
        }

        List<String> headerNames = new ArrayList<>(header.columnIndex.keySet());
        List<IngestionSupport.TableBinding> bindings = resolveBindings(sheetName, headerNames);
        if (bindings.isEmpty()) {
            log.warn("Sheet '{}' did not match any known entity; skipping", sheetName);
            accumulator.note("Sheet '" + sheetName + "' did not match any known entity and was skipped");
            return;
        }
        // Only noted once the sheet actually contributes data — a duplicate
        // header on a sheet that doesn't bind to anything isn't worth flagging.
        for (String duplicate : header.duplicateHeaders) {
            accumulator.note("Sheet '" + sheetName + "' has more than one column named '" + duplicate
                    + "'; only the first is used, the rest are ignored");
        }
        bindings.forEach(binding -> binding.notes().forEach(accumulator::note));

        int rows = 0;
        for (int r = header.rowNum + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isRowBlank(row, header.columnIndex)) {
                continue;
            }
            for (IngestionSupport.TableBinding binding : bindings) {
                accumulator.add(binding.entity(), IngestionSupport.reader(binding, column -> {
                    Integer col = header.columnIndex.get(column);
                    return col == null ? null : cellText(row.getCell(col));
                }));
            }
            rows++;
        }
        log.debug("Bound sheet '{}' to entit{} '{}' ({} row(s))",
                sheetName, bindings.size() == 1 ? "y" : "ies", bindings.stream()
                        .map(IngestionSupport.TableBinding::entity).toList(), rows);
    }

    /**
     * Resolves every entity type a sheet should be read as: entities explicitly
     * named to this sheet in configuration (a mapping sheet may name more than
     * one), otherwise the single best heuristic match.
     */
    private List<IngestionSupport.TableBinding> resolveBindings(String sheetName, List<String> headerNames) {
        List<String> explicit = IngestionSupport.entitiesByTableName(properties, sheetName);
        List<IngestionSupport.TableBinding> bindings = new ArrayList<>();
        if (!explicit.isEmpty()) {
            for (String entity : explicit) {
                IngestionSupport.TableBinding binding = IngestionSupport.bindEntity(properties, entity, headerNames);
                if (binding != null) {
                    bindings.add(binding);
                }
            }
        } else {
            IngestionSupport.TableBinding binding = IngestionSupport.bind(properties, sheetName, headerNames);
            if (binding != null) {
                bindings.add(binding);
            }
        }
        return bindings;
    }

    /**
     * Scans the leading rows and picks the one that yields the most columns
     * recognizable for some entity, so title/description rows are tolerated and
     * no fixed id/name column is required.
     */
    private HeaderRow findHeader(Sheet sheet, String sheetName) {
        int lastScan = Math.min(sheet.getLastRowNum(),
                sheet.getFirstRowNum() + properties.getMatching().getHeaderScanRows());
        HeaderRow best = null;
        double bestScore = -1;

        for (int r = sheet.getFirstRowNum(); r <= lastScan; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Integer> index = new LinkedHashMap<>();
            Set<String> seenNormalized = new HashSet<>();
            List<String> duplicates = new ArrayList<>();
            for (Cell cell : row) {
                String text = IngestionSupport.blankToNull(cellText(cell));
                if (text != null) {
                    if (!seenNormalized.add(IngestionSupport.normalize(text))) {
                        duplicates.add(text);
                    }
                    index.putIfAbsent(text, cell.getColumnIndex());
                }
            }
            if (index.size() < 2) {
                continue;
            }
            IngestionSupport.TableBinding candidate =
                    IngestionSupport.bind(properties, sheetName, new ArrayList<>(index.keySet()));
            double score = candidate == null ? 0 : candidate.confidence() * index.size();
            if (score > bestScore) {
                bestScore = score;
                best = new HeaderRow(r, index, duplicates);
            }
        }
        return best;
    }

    private boolean isRowBlank(Row row, Map<String, Integer> headerIndex) {
        for (Integer col : headerIndex.values()) {
            if (IngestionSupport.blankToNull(cellText(row.getCell(col))) != null) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Cell cell) {
        return cell == null ? null : dataFormatter.formatCellValue(cell);
    }

    /**
     * A detected header row: its index, the header-name -> column-index map,
     * and any header text that appeared more than once (only the first
     * occurrence of a duplicate is ever bound, per {@code columnIndex}'s
     * {@code putIfAbsent} above — this list is purely diagnostic).
     */
    private record HeaderRow(int rowNum, Map<String, Integer> columnIndex, List<String> duplicateHeaders) {
    }
}
