package com.vw.eacontext.api;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vw.eacontext.dto.DiagramExportRequest;
import com.vw.eacontext.dto.ExportFile;
import com.vw.eacontext.dto.FilterOptions;
import com.vw.eacontext.dto.Frame;
import com.vw.eacontext.dto.GapComparisonDto;
import com.vw.eacontext.dto.GraphDto;
import com.vw.eacontext.dto.ImpactAnalysisResult;
import com.vw.eacontext.dto.SimulatedRemovalResult;
import com.vw.eacontext.dto.SummaryResponse;
import com.vw.eacontext.insight.Finding;
import com.vw.eacontext.validation.ValidationReport;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * REST API for the EA context cartography.
 */
@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class EaController {

    private final EaContextService service;

    /**
     * Uploads a dataset (JSON, XLSX, or ZIP of CSVs), parses and validates it,
     * caches the model in memory, and returns the validation report.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationReport> upload(@RequestPart("file") MultipartFile file) {
        ValidationReport report = service.upload(file);
        // 200 with the report; data-quality issues are conveyed inside the report.
        return ResponseEntity.ok(report);
    }

    /**
     * Returns the node/edge projection for the requested observation frame.
     *
     * <p>With no {@code anchor}, this is the whole frame — unchanged behavior.
     * With an {@code anchor} (an application/process/information-object id, or a
     * business domain value for the domain frame), the response is a genuinely
     * scoped context diagram: only the anchor's neighborhood out to
     * {@code depth} hops, not the whole landscape with elements dimmed.</p>
     */
    @GetMapping(value = "/graph/{frame}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GraphDto> graph(
            @PathVariable @NotBlank String frame,
            @RequestParam(required = false) String anchor,
            @RequestParam(required = false) @Min(1) @Max(4) Integer depth) {
        return ResponseEntity.ok(service.graph(Frame.fromSlug(frame), anchor, depth));
    }

    /**
     * Returns the distinct selectable values for each filter dimension so a UI
     * can populate its pickers from the full model.
     */
    @GetMapping(value = "/filters", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FilterOptions> filters() {
        return ResponseEntity.ok(service.filterOptions());
    }

    /** Returns the blast radius (upstream + downstream) for an application. */
    @GetMapping(value = "/node/{id}/impact", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImpactAnalysisResult> impact(@PathVariable @NotBlank String id) {
        return ResponseEntity.ok(service.impact(id));
    }

    /** Returns the projected new/resolved findings if this application were retired. */
    @GetMapping(value = "/node/{id}/simulate-removal", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SimulatedRemovalResult> simulateRemoval(@PathVariable @NotBlank String id) {
        return ResponseEntity.ok(service.simulateRemoval(id));
    }

    /** Returns all insight findings for the cached model. */
    @GetMapping(value = "/insights", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Finding>> insights() {
        return ResponseEntity.ok(service.insights());
    }

    /** Returns the declared-vs-detected data-quality gap comparison. */
    @GetMapping(value = "/insights/gaps", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GapComparisonDto> insightGaps() {
        return ResponseEntity.ok(service.insightGaps());
    }

    /** Returns a natural-language summary of the cached model. */
    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SummaryResponse> summary() {
        return ResponseEntity.ok(new SummaryResponse(service.summary()));
    }

    /** Exports the current landscape as a downloadable png, pdf, or pptx file. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @Pattern(regexp = "png|pdf|pptx",
                    message = "type must be one of: png, pdf, pptx") String type) {
        return download(service.export(type));
    }

    /**
     * Exports one observation frame as an interoperable diagram file other
     * architecture tooling can open: {@code drawio} (draw.io / the Confluence
     * draw.io plugin) or {@code puml} (PlantUML source / the Confluence
     * PlantUML macro). The body carries the caller's rendered node geometry so
     * the exported diagram keeps the on-screen layout.
     */
    @PostMapping(value = "/export/diagram/{format}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportDiagram(
            @PathVariable @Pattern(regexp = "drawio|puml",
                    message = "format must be one of: drawio, puml") String format,
            @RequestBody DiagramExportRequest request) {
        return download(service.exportDiagram(format, request));
    }

    /** Wraps a generated file as an attachment download response. */
    private ResponseEntity<byte[]> download(ExportFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.filename()).build());
        return new ResponseEntity<>(file.content(), headers, HttpStatus.OK);
    }
}
