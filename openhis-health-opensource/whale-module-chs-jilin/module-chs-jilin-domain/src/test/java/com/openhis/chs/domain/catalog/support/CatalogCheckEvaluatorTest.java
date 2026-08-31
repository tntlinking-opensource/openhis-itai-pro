/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.support;

import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.openhis.chs.domain.notice.model.HisMappedProduct;
import com.openhis.chs.domain.notice.support.CatalogNoticeOverlayRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogCheckEvaluatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 27);

    @Test
    void missingYbNo_onlyThatIssue() {
        HisMappedProduct product = product("AD1", null, new BigDecimal("10"));
        List<CatalogCheckEvaluator.Finding> findings =
            CatalogCheckEvaluator.evaluate(product, catalog("1", LocalDate.of(2027, 1, 1)), null, true, DAY);
        assertEquals(1, findings.size());
        assertEquals(CatalogCheckIssueCodes.MISSING_YB_NO, findings.get(0).getIssueCode());
    }

    @Test
    void expiredAndDisabled() {
        HisMappedProduct product = product("AD1", "C01", new BigDecimal("10"));
        YbNationalCatalogItem catalog = catalog("0", LocalDate.of(2020, 1, 1));
        List<CatalogCheckEvaluator.Finding> findings =
            CatalogCheckEvaluator.evaluate(product, catalog, null, true, DAY);
        assertTrue(findings.stream().anyMatch(f -> CatalogCheckIssueCodes.EXPIRED.equals(f.getIssueCode())));
        assertTrue(findings.stream().anyMatch(f -> CatalogCheckIssueCodes.DISABLED.equals(f.getIssueCode())));
    }

    @Test
    void priceOver_whenLimitFromOverlay() {
        HisMappedProduct product = product("AD1", "C01", new BigDecimal("100"));
        YbNationalCatalogItem catalog = catalog("1", null);
        YbNationalCatalogNoticeOverlay overlay = new YbNationalCatalogNoticeOverlay();
        overlay.setNoticeAvaliFlag(CatalogNoticeOverlayRules.FLAG_AVAILABLE);
        overlay.setNoticePatchJson("{\"hilist_pric_uplmt_amt\":\"40\"}");
        List<CatalogCheckEvaluator.Finding> findings =
            CatalogCheckEvaluator.evaluate(product, catalog, overlay, true, DAY);
        assertEquals(1, findings.size());
        assertEquals(CatalogCheckIssueCodes.PRICE_OVER, findings.get(0).getIssueCode());
    }

    @Test
    void notInCatalog_onlyWhenMirrorPresent() {
        HisMappedProduct product = product("AD1", "C01", new BigDecimal("10"));
        List<CatalogCheckEvaluator.Finding> emptyMirror =
            CatalogCheckEvaluator.evaluate(product, null, null, false, DAY);
        assertTrue(emptyMirror.isEmpty());
        List<CatalogCheckEvaluator.Finding> present =
            CatalogCheckEvaluator.evaluate(product, null, null, true, DAY);
        assertEquals(1, present.size());
        assertEquals(CatalogCheckIssueCodes.NOT_IN_CATALOG, present.get(0).getIssueCode());
    }

    @Test
    void healthyRow_noFindings() {
        HisMappedProduct product = product("AD1", "C01", new BigDecimal("10"));
        YbNationalCatalogItem catalog = catalog("1", LocalDate.of(2027, 1, 1));
        List<CatalogCheckEvaluator.Finding> findings =
            CatalogCheckEvaluator.evaluate(product, catalog, null, true, DAY);
        assertTrue(findings.isEmpty());
    }

    @Test
    void noticeExpired_fromPatchEndDate() {
        HisMappedProduct product = product("AD1", "C01", new BigDecimal("10"));
        YbNationalCatalogNoticeOverlay overlay = new YbNationalCatalogNoticeOverlay();
        overlay.setNoticeAvaliFlag(CatalogNoticeOverlayRules.FLAG_AVAILABLE);
        overlay.setNoticePatchJson("{\"begin_date\":\"2024-01-01\",\"end_date\":\"2025-12-31\"}");
        List<CatalogCheckEvaluator.Finding> findings =
            CatalogCheckEvaluator.evaluate(product, catalog("1", null), overlay, true, DAY);
        assertEquals(1, findings.size());
        assertEquals(CatalogCheckIssueCodes.NOTICE_EXPIRED, findings.get(0).getIssueCode());
    }

    @Test
    void noticeNotEffective_fromPatchBeginDate() {
        HisMappedProduct product = product("AD1", "C01", new BigDecimal("10"));
        YbNationalCatalogNoticeOverlay overlay = new YbNationalCatalogNoticeOverlay();
        overlay.setNoticeAvaliFlag(CatalogNoticeOverlayRules.FLAG_AVAILABLE);
        overlay.setNoticePatchJson("{\"begin_date\":\"2026-09-01\",\"end_date\":\"2027-12-31\"}");
        List<CatalogCheckEvaluator.Finding> findings =
            CatalogCheckEvaluator.evaluate(product, catalog("1", null), overlay, true, DAY);
        assertEquals(1, findings.size());
        assertEquals(CatalogCheckIssueCodes.NOTICE_NOT_EFFECTIVE, findings.get(0).getIssueCode());
    }

    @Test
    void noticeStop_doesNotAlsoReportExpired() {
        HisMappedProduct product = product("AD1", "C01", new BigDecimal("10"));
        YbNationalCatalogNoticeOverlay overlay = new YbNationalCatalogNoticeOverlay();
        overlay.setNoticeAvaliFlag(CatalogNoticeOverlayRules.FLAG_STOP);
        overlay.setNoticeEndDate(LocalDate.of(2026, 1, 1));
        overlay.setNoticePatchJson("{\"end_date\":\"2026-01-01\"}");
        List<CatalogCheckEvaluator.Finding> findings =
            CatalogCheckEvaluator.evaluate(product, catalog("1", null), overlay, true, DAY);
        assertEquals(1, findings.size());
        assertEquals(CatalogCheckIssueCodes.NOTICE_STOP, findings.get(0).getIssueCode());
    }

    @Test
    void noticeExpired_fallsBackToOverlayNoticeEndDate() {
        HisMappedProduct product = product("AD1", "C01", new BigDecimal("10"));
        YbNationalCatalogNoticeOverlay overlay = new YbNationalCatalogNoticeOverlay();
        overlay.setNoticeAvaliFlag(CatalogNoticeOverlayRules.FLAG_AVAILABLE);
        overlay.setNoticeEndDate(LocalDate.of(2025, 6, 30));
        List<CatalogCheckEvaluator.Finding> findings =
            CatalogCheckEvaluator.evaluate(product, catalog("1", null), overlay, true, DAY);
        assertEquals(1, findings.size());
        assertEquals(CatalogCheckIssueCodes.NOTICE_EXPIRED, findings.get(0).getIssueCode());
    }

    private static HisMappedProduct product(String busNo, String ybNo, BigDecimal sale) {
        HisMappedProduct product = new HisMappedProduct();
        product.setBusNo(busNo);
        product.setYbNo(ybNo);
        product.setSalePrice(sale);
        product.setName("清创");
        return product;
    }

    private static YbNationalCatalogItem catalog(String valiFlag, LocalDate enddate) {
        YbNationalCatalogItem item = new YbNationalCatalogItem();
        item.setHilistCode("C01");
        item.setValiFlag(valiFlag);
        item.setEnddate(enddate);
        return item;
    }
}
