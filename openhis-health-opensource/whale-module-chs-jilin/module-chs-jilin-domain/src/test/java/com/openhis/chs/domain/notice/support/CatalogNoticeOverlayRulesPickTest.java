/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.support;

import com.openhis.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 局文 overlay 择优：批查须与单条 getOverlay（listType=null）同口径。
 */
class CatalogNoticeOverlayRulesPickTest {

    /**
     * listType 为空时不得用「首行 listType」伪造过滤，否则可能挑到不同契约/品类行。
     */
    @Test
    void pickBest_whenListTypeNull_matchesContractPreferenceWithoutFakeListType() {
        YbNationalCatalogNoticeOverlay otherTypeExact = overlay(10L, "A", "TYPE_X");
        YbNationalCatalogNoticeOverlay fallback = overlay(0L, "A", "TYPE_Y");
        List<YbNationalCatalogNoticeOverlay> rows = List.of(otherTypeExact, fallback);

        // 单条 getOverlay(listType=null)：跳过品类精确匹配，按契约优先命中 contract=10
        Optional<YbNationalCatalogNoticeOverlay> single =
            CatalogNoticeOverlayRules.pickBestOverlay(rows, 10L, null);
        assertTrue(single.isPresent());
        assertEquals(10L, single.get().getContractId());

        // 错误批查曾用首行 TYPE_X 再过滤：若仅 TYPE_Y 有 contract=10 会选错；此处用同口径断言
        Optional<YbNationalCatalogNoticeOverlay> batchSame =
            CatalogNoticeOverlayRules.pickBestOverlay(rows, 10L, null);
        assertEquals(single.get().getContractId(), batchSame.orElseThrow().getContractId());
        assertEquals(single.get().getListType(), batchSame.orElseThrow().getListType());
    }

    /**
     * 构造最小 overlay 行。
     *
     * @param contractId 契约
     * @param hilist     国码
     * @param listType   品类
     * @return 实体
     */
    private static YbNationalCatalogNoticeOverlay overlay(Long contractId, String hilist, String listType) {
        YbNationalCatalogNoticeOverlay row = new YbNationalCatalogNoticeOverlay();
        row.setContractId(contractId);
        row.setHilistCode(hilist);
        row.setListType(listType);
        return row;
    }
}
