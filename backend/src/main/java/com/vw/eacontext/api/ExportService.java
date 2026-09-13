package com.vw.eacontext.api;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.stereotype.Service;

import com.vw.eacontext.dto.ExportFile;
import com.vw.eacontext.dto.GraphStats;
import com.vw.eacontext.exception.EaIngestionException;
import com.vw.eacontext.insight.Finding;

import lombok.extern.slf4j.Slf4j;

/**
 * Renders a textual/summary export of the current landscape as PNG, PDF or PPTX.
 *
 * <p>These are lightweight, dependency-only renders (stats + key findings)
 * suitable for sharing; a full visual diagram render would require a headless
 * browser and is out of scope here.</p>
 */
@Slf4j
@Service
public class ExportService {

    public ExportFile export(String type, GraphStats stats, List<Finding> findings) {
        String normalized = type == null ? "" : type.toLowerCase();
        List<String> lines = buildLines(stats, findings);
        try {
            return switch (normalized) {
                case "png" -> new ExportFile("ea-context.png", "image/png", renderPng(lines));
                case "pdf" -> new ExportFile("ea-context.pdf", "application/pdf", renderPdf(lines));
                case "pptx" -> new ExportFile("ea-context.pptx",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        renderPptx(lines));
                default -> throw new IllegalArgumentException(
                        "Unsupported export type '" + type + "'. Valid values: png, pdf, pptx");
            };
        } catch (IOException e) {
            throw new EaIngestionException("Failed to generate " + normalized + " export", e);
        }
    }

    private List<String> buildLines(GraphStats stats, List<Finding> findings) {
        List<String> lines = new ArrayList<>();
        lines.add("Enterprise Architecture Context");
        if (stats != null) {
            lines.add("Applications: " + stats.applicationCount()
                    + "   Relationships: " + stats.relationshipCount()
                    + "   Interfaces: " + stats.interfaceCount()
                    + "   Domains: " + stats.domainCount());
            lines.add("Business processes: " + stats.businessProcessCount()
                    + "   Information flows: " + stats.informationObjectCount());
            if (stats.mostConnectedApplicationId() != null) {
                lines.add("Most connected (hub): " + stats.mostConnectedApplicationId()
                        + " (in-degree " + stats.maxDegree() + ")");
            }
            lines.add("Hubs: " + stats.hubCount()
                    + "   Circular dependencies: " + stats.cycleCount()
                    + "   Orphan applications: " + stats.orphanCount());
        }
        lines.add("");
        lines.add("Findings: " + (findings == null ? 0 : findings.size()));
        if (findings != null) {
            findings.stream().limit(20).forEach(f ->
                    lines.add("- [" + f.severity() + "] " + f.type() + " " + f.relatedEntityIds() + ": "
                            + f.message()));
        }
        return lines;
    }

    private byte[] renderPng(List<String> lines) throws IOException {
        int width = 900;
        int height = Math.max(300, 60 + lines.size() * 22);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fill(new Rectangle(0, 0, width, height));
        g.setColor(new Color(0x0E, 0x4A, 0x47));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        int y = 30;
        boolean first = true;
        for (String line : lines) {
            g.drawString(line, 20, y);
            y += 22;
            if (first) {
                g.setColor(Color.DARK_GRAY);
                g.setFont(new Font("SansSerif", Font.PLAIN, 13));
                first = false;
            }
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] renderPdf(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.setLeading(16f);
                cs.beginText();
                cs.newLineAtOffset(50, 780);
                boolean first = true;
                for (String line : lines) {
                    cs.setFont(new PDType1Font(first
                            ? Standard14Fonts.FontName.HELVETICA_BOLD
                            : Standard14Fonts.FontName.HELVETICA), first ? 16 : 11);
                    cs.showText(sanitize(line));
                    cs.newLine();
                    first = false;
                }
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] renderPptx(List<String> lines) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setAnchor(new Rectangle(30, 30, 660, 480));
            boolean first = true;
            for (String line : lines) {
                XSLFTextParagraph p = box.addNewTextParagraph();
                XSLFTextRun run = p.addNewTextRun();
                run.setText(line);
                run.setFontSize(first ? 24.0 : 12.0);
                run.setBold(first);
                first = false;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ppt.write(out);
            return out.toByteArray();
        }
    }

    /** PDFBox Standard-14 fonts only support WinAnsi; drop unsupported chars. */
    private String sanitize(String text) {
        return text.replaceAll("[^\\x20-\\x7E]", "-");
    }
}

