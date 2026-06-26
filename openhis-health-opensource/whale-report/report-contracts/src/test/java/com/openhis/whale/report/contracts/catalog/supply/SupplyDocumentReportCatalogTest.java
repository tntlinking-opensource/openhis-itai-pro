package com.openhis.whale.report.contracts.catalog.supply;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyDocumentReportCatalogTest {

    @Test
    void findByReportCodeResolvesWarehouseDocument() {
        var spec = SupplyDocumentReportCatalog.findByReportCode("supply_cabinet_stock_out");
        assertTrue(spec.isPresent());
        assertEquals("CABINET_STOCK_OUT", spec.get().supplyTypeCode());
    }

    @Test
    void isSupplyDocumentReportReturnsFalseForUnknownCode() {
        assertTrue(SupplyDocumentReportCatalog.isSupplyDocumentReport("supply_cabinet_stock_out"));
        assertFalse(SupplyDocumentReportCatalog.isSupplyDocumentReport("unknown_report"));
    }

    @Test
    void sortedPreservesSortOrder() {
        var list = SupplyDocumentReportCatalog.sorted();
        assertTrue(list.size() > 5);
        assertTrue(list.get(0).sortOrder() <= list.get(1).sortOrder());
    }
}
