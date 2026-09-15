package com.vw.eacontext.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

/**
 * A workbook edited in Excel stores dates as real date cells (rendered through a
 * display format such as Excel's default "m/d/yy") and may use formulas. Both
 * must ingest by value, not by how the cell happens to be displayed.
 */
@SpringBootTest
class ExcelCellValueTest {

    @Autowired
    private ExcelEaDataParser parser;

    @Test
    void readsRealDateCellsAndFormulaResultsByValue() throws Exception {
        CanonicalModel model = parser.parse(new ByteArrayInputStream(workbook()));

        Application app = model.applications().get(0);
        assertThat(app.lifecycleStartDate()).isEqualTo(LocalDate.of(2019, 4, 1));
        assertThat(app.lifecycleEndDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(app.costCenter()).isEqualTo("CC-100");
        assertThat(model.ingestionNotes()).noneMatch(note -> note.contains("unparseable"));
    }

    private static byte[] workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Applications");
            String[] header = {"ApplicationID", "ApplicationName", "BusinessDomain", "LifecycleStatus",
                    "LifecycleStartDate", "LifecycleEndDate", "CostCenter"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                headerRow.createCell(i).setCellValue(header[i]);
            }

            CellStyle shortDate = workbook.createCellStyle();
            shortDate.setDataFormat(workbook.createDataFormat().getFormat("m/d/yy"));
            CellStyle longDate = workbook.createCellStyle();
            longDate.setDataFormat(workbook.createDataFormat().getFormat("d-mmm-yyyy"));

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("APP-1");
            row.createCell(1).setCellValue("Billing");
            row.createCell(2).setCellValue("Finance");
            row.createCell(3).setCellValue("Active");
            row.createCell(4).setCellValue(LocalDate.of(2019, 4, 1));
            row.getCell(4).setCellStyle(longDate);
            row.createCell(5).setCellValue(LocalDate.of(2026, 1, 15));
            row.getCell(5).setCellStyle(shortDate);
            row.createCell(6).setCellFormula("\"CC-\"&100");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
