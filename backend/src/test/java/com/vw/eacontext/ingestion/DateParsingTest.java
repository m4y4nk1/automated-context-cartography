package com.vw.eacontext.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DateParsingTest {

    private static final LocalDate JAN_15_2026 = LocalDate.of(2026, 1, 15);

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-01-15",
            "2026-01-15T00:00:00",
            "2026-01-15T08:30:00Z",
            "2026-01-15T08:30:00+05:30",
            "2026-01-15 00:00:00",
            "2026-01-15 08:30",
            "1/15/2026",
            "1/15/26",
            "15/01/2026",
            "15.01.2026",
            "2026/01/15",
            "15-Jan-2026",
            "  2026-01-15  ",
    })
    void acceptsCommonExportAndSpreadsheetRenderings(String raw) {
        assertThat(IngestionSupport.parseIsoDate(raw)).isEqualTo(JAN_15_2026);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a date", "2026-13-45", "Q1 2026"})
    void unparseableValuesResolveToNullRatherThanThrowing(String raw) {
        assertThat(IngestionSupport.parseIsoDate(raw)).isNull();
    }

    @org.junit.jupiter.api.Test
    void anAmbiguousSlashDateReadsMonthFirst() {
        assertThat(IngestionSupport.parseIsoDate("03/04/2026")).isEqualTo(LocalDate.of(2026, 3, 4));
    }
}
