package com.openhis.whale.report.adapter.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportDatasetParamSupportTest {

    @Test
    void resolveDateRangeClampsEndBeforeStart() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        Map<String, Object> params = Map.of("dateTo", "2026-05-01");
        LocalDate to = ReportDatasetParamSupport.resolveDateRangeTo(params, from);
        assertEquals(from, to);
    }

    @Test
    void extractLongParsesStringValue() {
        Map<String, Object> params = Map.of("organizationId", "12345");
        Long id = ReportDatasetParamSupport.extractLong(params, "organizationId");
        assertEquals(12345L, id);
    }

    @Test
    void copyParamsNeverReturnsNull() {
        assertNotNull(ReportDatasetParamSupport.copyParams(null));
    }
}
