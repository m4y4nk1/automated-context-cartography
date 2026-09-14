package com.vw.eacontext.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import com.vw.eacontext.exception.EaIngestionException;
import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;
import com.vw.eacontext.model.Interface;
import com.vw.eacontext.model.InterfaceStatus;
import com.vw.eacontext.model.LifecycleStatus;
import com.vw.eacontext.model.Protocol;

@SpringBootTest
class ExcelEaDataParserTest {

    @Autowired
    private ExcelEaDataParser parser;

    @Test
    void parsesSampleWorkbookUsingSharedMapping() throws Exception {
        CanonicalModel model;
        try (InputStream in = new ClassPathResource("sample_ea_dataset.xlsx").getInputStream()) {
            model = parser.parse(in);
        }

        // Counts across the per-entity sheets, including the BusinessProcesses
        // mapping sheet fanning out into both businessProcesses and processMappings.
        assertThat(model.applications()).hasSize(12);
        assertThat(model.relationships()).hasSize(13);
        assertThat(model.interfaces()).hasSize(6);
        assertThat(model.informationObjects()).hasSize(5);
        assertThat(model.businessProcesses()).hasSize(3);
        assertThat(model.processMappings()).hasSize(11);
        assertThat(model.applicationOwnerships()).hasSize(9);
        assertThat(model.dataQualityGaps()).hasSize(5);

        Application crm = model.applications().stream()
                .filter(a -> "APP-CRM".equals(a.id())).findFirst().orElseThrow();
        assertThat(crm.name()).isEqualTo("CRM Suite");
        assertThat(crm.businessDomain()).isEqualTo("Sales & Ordering");
        assertThat(crm.lifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);

        Interface legacyExtract = model.interfaces().stream()
                .filter(i -> "IF-004".equals(i.id())).findFirst().orElseThrow();
        assertThat(legacyExtract.providerApplicationId()).isEqualTo("APP-LEGACY");
        assertThat(legacyExtract.consumerApplicationId()).isEqualTo("APP-BILL");
        assertThat(legacyExtract.protocol()).isEqualTo(Protocol.SFTP_FILE);
        assertThat(legacyExtract.interfaceStatus()).isEqualTo(InterfaceStatus.ACTIVE);
    }

    @Test
    void duplicateHeaderColumnIsNoted() throws Exception {
        // The second "BusinessDomain" cell is left blank on the data row (as a
        // duplicate column typically is in practice) so the header-detection
        // heuristic — which scores candidate rows by their count of distinct,
        // non-blank cells — still favors the real header row over the data row,
        // even though the duplicate collapses the header's own count by one.
        byte[] workbook = singleSheetWorkbook("Applications",
                new String[] {"ApplicationID", "ApplicationName", "BusinessDomain", "BusinessDomain"},
                new String[] {"APP-CRM", "Customer CRM", "Sales", ""});

        CanonicalModel model = parser.parse(new ByteArrayInputStream(workbook));

        assertThat(model.ingestionNotes()).anyMatch(note -> note.contains("more than one column named")
                && note.contains("BusinessDomain") && note.contains("only the first is used"));
    }

    @Test
    void malformedFileProducesACleanIngestionExceptionNotARawPoiError() {
        // Plain text renamed .xlsx — not a valid OOXML zip at all, so POI's
        // format detection fails at workbook-open time, not mid-parse.
        InputStream notAWorkbook = new ByteArrayInputStream(
                "this is not an excel file".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parse(notAWorkbook))
                .isInstanceOf(EaIngestionException.class)
                .hasMessageContaining("doesn't look like a valid .xlsx workbook");
    }

    private static byte[] singleSheetWorkbook(String sheetName, String[] header, String[] dataRow)
            throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                headerRow.createCell(i).setCellValue(header[i]);
            }
            Row row = sheet.createRow(1);
            for (int i = 0; i < dataRow.length; i++) {
                row.createCell(i).setCellValue(dataRow[i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
