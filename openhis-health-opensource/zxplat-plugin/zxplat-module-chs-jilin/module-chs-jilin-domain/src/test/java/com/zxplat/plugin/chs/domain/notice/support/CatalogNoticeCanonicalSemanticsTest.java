/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.support;

import com.zxplat.plugin.chs.domain.notice.command.PersistNoticeLineCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标准包语义：业务日、脏码跳行、物价码挪列、已停不复活。
 */
class CatalogNoticeCanonicalSemanticsTest {

    /**
     * 通知日、生效日任一有值即可排序应用。
     */
    @Test
    void hasBusinessDate_acceptsEitherField() {
        assertTrue(CatalogNoticeCanonicalSemantics.hasBusinessDate(LocalDate.parse("2026-03-24"), null));
        assertTrue(CatalogNoticeCanonicalSemantics.hasBusinessDate(null, LocalDate.parse("2025-03-11")));
        assertFalse(CatalogNoticeCanonicalSemantics.hasBusinessDate(null, null));
    }

    /**
     * 未赋码与汉字不得当编码。
     */
    @Test
    void isDirtyCode_rejectsChinesePlaceholder() {
        assertTrue(CatalogNoticeCanonicalSemantics.isDirtyCode("国家未赋码"));
        assertFalse(CatalogNoticeCanonicalSemantics.isDirtyCode("012302010010000"));
        assertFalse(CatalogNoticeCanonicalSemantics.isDirtyCode(null));
    }

    /**
     * 01* 与 37* 视为物价码；缺位与 T 后缀仍算物价码，不要求 15 位。
     */
    @Test
    void isPriceItemCode_accepts01And37WithSuffix() {
        assertTrue(CatalogNoticeCanonicalSemantics.isPriceItemCode("012404000010000"));
        assertTrue(CatalogNoticeCanonicalSemantics.isPriceItemCode("016200000870000T"));
        assertTrue(CatalogNoticeCanonicalSemantics.isPriceItemCode("371102020010001"));
        assertFalse(CatalogNoticeCanonicalSemantics.isPriceItemCode("003115010030000-311502009(z)"));
    }

    /**
     * hilist 误放物价码且 project 空时只挪列，不补 0。
     */
    @Test
    void sanitizeLine_movesPriceCodeOffHilistWhenProjectEmpty() {
        PersistNoticeLineCommand line = new PersistNoticeLineCommand();
        line.setHilistCode("371102020010001");
        CatalogNoticeCanonicalSemantics.sanitizeLine(line);
        assertEquals("371102020010001", line.getProjectCode());
        assertNull(line.getHilistCode());
        assertFalse(CatalogNoticeCanonicalSemantics.shouldSkipLine(line));
    }

    /**
     * 脏码行跳过入库。
     */
    @Test
    void shouldSkipLine_dirtyProject() {
        PersistNoticeLineCommand line = new PersistNoticeLineCommand();
        line.setProjectCode("国家未赋码");
        CatalogNoticeCanonicalSemantics.sanitizeLine(line);
        assertTrue(CatalogNoticeCanonicalSemantics.shouldSkipLine(line));
    }

    /**
     * 已停标志保持停用。
     */
    @Test
    void shouldKeepStopped_whenFlagZero() {
        assertTrue(CatalogNoticeCanonicalSemantics.shouldKeepStopped("0"));
        assertFalse(CatalogNoticeCanonicalSemantics.shouldKeepStopped("1"));
        assertFalse(CatalogNoticeCanonicalSemantics.shouldKeepStopped(null));
    }
}
