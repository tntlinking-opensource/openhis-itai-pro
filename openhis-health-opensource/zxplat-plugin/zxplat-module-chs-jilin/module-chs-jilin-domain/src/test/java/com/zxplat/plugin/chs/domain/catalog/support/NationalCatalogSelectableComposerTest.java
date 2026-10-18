/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

import com.zxplat.plugin.chs.domain.catalog.result.NationalCatalogItemResult;
import com.zxplat.plugin.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.zxplat.plugin.chs.domain.notice.support.CatalogNoticeOverlayRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationalCatalogSelectableComposerTest {

    @Test
    void compose_mergesPatchAndSelectableWhenNoStop() {
        NationalCatalogItemResult row = new NationalCatalogItemResult()
            .setHilistCode("X01")
            .setHilistName("镜像名")
            .setChrgitmLv("03")
            .setValiFlag("1");
        YbNationalCatalogNoticeOverlay overlay = new YbNationalCatalogNoticeOverlay();
        overlay.setNoticeAvaliFlag(CatalogNoticeOverlayRules.FLAG_AVAILABLE);
        overlay.setNoticePatchJson("{\"chrgitm_lv\":\"01\",\"hilist_pric_uplmt_amt\":\"12.5\"}");

        NationalCatalogSelectableComposer.compose(row, overlay, LocalDate.of(2026, 7, 15));

        assertTrue(row.getIsSelectable());
        assertNull(row.getUnselectableReason());
        assertEquals("01", row.getChrgitmLv());
        assertEquals("12.5", row.getPriceUplmtAmt());
        assertEquals("镜像名", row.getHilistName());
    }

    @Test
    void compose_noticeStopBlocksSelection() {
        NationalCatalogItemResult row = new NationalCatalogItemResult()
            .setHilistCode("X02")
            .setValiFlag("1");
        YbNationalCatalogNoticeOverlay overlay = new YbNationalCatalogNoticeOverlay();
        overlay.setNoticeAvaliFlag(CatalogNoticeOverlayRules.FLAG_STOP);
        overlay.setNoticeEndDate(LocalDate.of(2026, 1, 1));

        NationalCatalogSelectableComposer.compose(row, overlay, LocalDate.of(2026, 7, 15));

        assertFalse(row.getIsSelectable());
        assertTrue(row.getUnselectableReason().contains(NationalCatalogSelectableComposer.REASON_NOTICE_STOP));
    }

    @Test
    void compose_platformInvalidBlocksBeforeNotice() {
        NationalCatalogItemResult row = new NationalCatalogItemResult()
            .setHilistCode("X03")
            .setValiFlag("0");

        NationalCatalogSelectableComposer.compose(row, null, LocalDate.of(2026, 7, 15));

        assertFalse(row.getIsSelectable());
        assertEquals(NationalCatalogSelectableComposer.REASON_PLATFORM_INVALID, row.getUnselectableReason());
    }

    @Test
    void compose_noticeStopNotYetEffective_stillSelectable() {
        NationalCatalogItemResult row = new NationalCatalogItemResult()
            .setHilistCode("X04")
            .setValiFlag("1");
        YbNationalCatalogNoticeOverlay overlay = new YbNationalCatalogNoticeOverlay();
        overlay.setNoticeAvaliFlag(CatalogNoticeOverlayRules.FLAG_STOP);
        overlay.setNoticeEndDate(LocalDate.of(2026, 12, 31));

        NationalCatalogSelectableComposer.compose(row, overlay, LocalDate.of(2026, 7, 15));

        assertTrue(row.getIsSelectable());
    }

    @Test
    void compose_platformExpiredBlocksSelection() {
        NationalCatalogItemResult row = new NationalCatalogItemResult()
            .setHilistCode("X05")
            .setValiFlag("1")
            .setEnddate(LocalDate.of(2020, 1, 1));

        NationalCatalogSelectableComposer.compose(row, null, LocalDate.of(2026, 7, 15));

        assertFalse(row.getIsSelectable());
        assertEquals(NationalCatalogSelectableComposer.REASON_PLATFORM_EXPIRED, row.getUnselectableReason());
    }

    @Test
    void compose_enddateTodayStillSelectable() {
        LocalDate day = LocalDate.of(2026, 7, 15);
        NationalCatalogItemResult row = new NationalCatalogItemResult()
            .setHilistCode("X06")
            .setValiFlag("1")
            .setEnddate(day);

        NationalCatalogSelectableComposer.compose(row, null, day);

        assertTrue(row.getIsSelectable());
        assertNull(row.getUnselectableReason());
    }
}
