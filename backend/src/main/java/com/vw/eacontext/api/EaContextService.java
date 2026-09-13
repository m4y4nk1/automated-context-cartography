package com.vw.eacontext.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vw.eacontext.ai.SummaryGenerator;
import com.vw.eacontext.dto.ExportFile;
import com.vw.eacontext.dto.FilterOptions;
import com.vw.eacontext.dto.Frame;
import com.vw.eacontext.dto.GapComparisonDto;
import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.ImpactAnalysisResult;
import com.vw.eacontext.exception.EaIngestionException;
import com.vw.eacontext.graph.GraphProjectionService;
import com.vw.eacontext.graph.ImpactAnalysisService;
import com.vw.eacontext.ingestion.CsvEaDataParser;
import com.vw.eacontext.ingestion.EaDataParser;
import com.vw.eacontext.ingestion.ExcelEaDataParser;
import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.insight.GapComparison;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.validation.ValidationReport;
import com.vw.eacontext.validation.ValidationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application service for the REST layer. Parsing/validation happens on upload;
 * all read operations reuse the derived artifacts cached in
 * {@link SessionModelStore}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EaContextService {

    private final JsonEaDataParser jsonParser;
    private final ExcelEaDataParser excelParser;
    private final CsvEaDataParser csvParser;
    private final ValidationService validationService;
    private final GraphProjectionService projectionService;
    private final ImpactAnalysisService impactAnalysisService;
    private final SummaryGenerator summaryGenerator;
    private final ExportService exportService;
    private final FilterOptionsService filterOptionsService;
    private final SessionModelStore store;

    /**
     * Parses the uploaded file (JSON, XLSX, or ZIP-of-CSVs), caches the model
     * and its derived artifacts, and returns a validation report.
     */
    public ValidationReport upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EaIngestionException("Uploaded file is empty");
        }
        EaDataParser parser = parserFor(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            CanonicalModel model = parser.parse(in);
            store.load(model);
            log.info("Cached model from '{}'", file.getOriginalFilename());
            return validationService.validate(model);
        } catch (IOException e) {
            throw new EaIngestionException("Failed to read uploaded file", e);
        }
    }

    /** Projects the requested observation frame from the cached model/graph. */
    public GraphDto graph(Frame frame) {
        return switch (frame) {
            case APPLICATION -> projectionService.applicationView(store.getModel(), store.getGraph());
            case PROCESS -> projectionService.businessProcessView(store.getModel());
            case DOMAIN -> projectionService.domainView(store.getModel(), store.getGraph());
            case INFO_FLOW -> projectionService.informationFlowView(store.getModel());
        };
    }

    public ImpactAnalysisResult impact(String appId) {
        return impactAnalysisService.impactAnalysis(store.getGraph(), appId);
    }

    /** @return the selectable values for each filter dimension. */
    public FilterOptions filterOptions() {
        return filterOptionsService.optionsFor(store.getModel());
    }

    public List<Finding> insights() {
        return store.getFindings();
    }

    /** @return the declared-vs-detected data-quality gap comparison. */
    public GapComparisonDto insightGaps() {
        GapComparison comparison = store.getGapComparison();
        return new GapComparisonDto(
                comparison.declaredCount(), comparison.detectedCount(), comparison.newlyDetected());
    }

    public String summary() {
        return summaryGenerator.summarize(store.getFindings(), store.getStats());
    }

    public ExportFile export(String type) {
        return exportService.export(type, store.getStats(), store.getFindings());
    }

    private EaDataParser parserFor(String filename) {
        String name = filename == null ? "" : filename.toLowerCase();
        if (name.endsWith(".json")) {
            return jsonParser;
        }
        if (name.endsWith(".xlsx")) {
            return excelParser;
        }
        if (name.endsWith(".zip")) {
            return csvParser;
        }
        throw new EaIngestionException(
                "Unsupported file type '" + filename + "'. Provide .json, .xlsx, or .zip (CSV files).");
    }
}
