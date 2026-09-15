package com.vw.eacontext.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vw.eacontext.ai.SummaryGenerator;
import com.vw.eacontext.dto.DiagramExportRequest;
import com.vw.eacontext.dto.ExportFile;
import com.vw.eacontext.dto.FilterOptions;
import com.vw.eacontext.dto.Frame;
import com.vw.eacontext.dto.GapComparisonDto;
import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.ImpactAnalysisResult;
import com.vw.eacontext.dto.SimulatedRemovalResult;
import com.vw.eacontext.exception.EaIngestionException;
import com.vw.eacontext.graph.GraphProjectionService;
import com.vw.eacontext.graph.GraphScopeService;
import com.vw.eacontext.graph.ImpactAnalysisService;
import com.vw.eacontext.ingestion.CsvEaDataParser;
import com.vw.eacontext.ingestion.EaDataParser;
import com.vw.eacontext.ingestion.ExcelEaDataParser;
import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.insight.ChangeSimulationService;
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
    private final GraphScopeService graphScopeService;
    private final ImpactAnalysisService impactAnalysisService;
    private final ChangeSimulationService changeSimulationService;
    private final SummaryGenerator summaryGenerator;
    private final ExportService exportService;
    private final DrawioExportService drawioExportService;
    private final PlantUmlExportService plantUmlExportService;
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
            case DOMAIN -> projectionService.domainView(store.getModel());
            case INFO_FLOW -> projectionService.informationFlowView(store.getModel());
        };
    }

    /**
     * Projects the requested observation frame, scoped to one anchor's
     * neighborhood when {@code anchor} is given — a genuinely reduced diagram,
     * not the whole frame with some elements dimmed. A blank/{@code null}
     * anchor is identical to {@link #graph(Frame)}.
     *
     * @param anchor node id (application/process/infoflow frames) or business
     *               domain value (domain frame) to anchor on
     * @param depth  hops out from the anchor to include; {@code null} defaults to 1
     */
    public GraphDto graph(Frame frame, String anchor, Integer depth) {
        if (anchor == null || anchor.isBlank()) {
            return graph(frame);
        }
        int hops = depth == null ? 1 : depth;
        if (frame == Frame.DOMAIN) {
            // The domain frame's own DTO is domain-to-domain bubbles; anchoring on
            // a domain should show that domain's applications ("the Customer
            // Service domain application ecosystem"), so scope the application
            // frame instead, seeded by every app in that domain.
            GraphDto appView = projectionService.applicationView(store.getModel(), store.getGraph());
            // The domain frame's "Unassigned" node stands for every real
            // application with a blank domain — not for ghost placeholders.
            boolean unassigned = GraphProjectionService.UNASSIGNED_DOMAIN.equals(anchor);
            return graphScopeService.scopeByAttribute(appView, node -> {
                Object domain = node.data().get("businessDomain");
                return unassigned
                        ? "application".equals(node.type()) && (domain == null || domain.toString().isBlank())
                        : anchor.equals(domain);
            }, hops);
        }
        return graphScopeService.scope(graph(frame), anchor, hops);
    }

    public ImpactAnalysisResult impact(String appId) {
        return impactAnalysisService.impactAnalysis(store.getGraph(), appId);
    }

    /** @return the projected new/resolved findings if {@code appId} were retired. */
    public SimulatedRemovalResult simulateRemoval(String appId) {
        return changeSimulationService.simulateRemoval(
                store.getModel(), store.getGraph(), store.getFindings(), appId);
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

    /**
     * Exports one observation frame as an interoperable diagram file that other
     * architecture tooling can open — draw.io XML (also the Confluence draw.io
     * plugin) or PlantUML source (the Confluence PlantUML macro).
     *
     * @param format {@code drawio} or {@code puml}
     * @param request the frame to export plus the caller's rendered geometry
     */
    public ExportFile exportDiagram(String format, DiagramExportRequest request) {
        Frame frame = Frame.fromSlug(request.frame());
        GraphDto graphDto = graph(frame);
        String normalized = format == null ? "" : format.toLowerCase();
        return switch (normalized) {
            case "drawio" -> drawioExportService.toDrawio(graphDto, frame, request, flaggedEntityIds());
            case "puml" -> plantUmlExportService.toPlantUml(graphDto, frame, request);
            default -> throw new IllegalArgumentException(
                    "Unsupported diagram export format '" + format + "'. Valid values: drawio, puml");
        };
    }

    /** Every entity id carrying at least one finding, so exports can flag them. */
    private Set<String> flaggedEntityIds() {
        return store.getFindings().stream()
                .flatMap(finding -> finding.relatedEntityIds().stream())
                .collect(Collectors.toSet());
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
