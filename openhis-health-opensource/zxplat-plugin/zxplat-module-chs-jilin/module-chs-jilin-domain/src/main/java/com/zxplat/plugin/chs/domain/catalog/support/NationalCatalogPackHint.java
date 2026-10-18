/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import java.math.BigDecimal;

/**
 * 国家目录 1301 包装/计价单位（限价口径）。
 *
 * <p>优先来自表列 {@code min_pac_cnt}/{@code min_pacunt}/{@code min_prepunt}/{@code min_prcunt}
 * （对应 raw_json 数组下标 41/42/43/48，规范列号 42/43/44/49）。</p>
 *
 * @param hilistCode 国家码
 * @param minPacCnt  最小包装数量
 * @param minPacunt  最小包装单位
 * @param minPrepunt 最小制剂单位
 * @param minPrcunt  最小计价单位（限价单位）
 */
public record NationalCatalogPackHint(
    String hilistCode,
    String minPacCnt,
    String minPacunt,
    String minPrepunt,
    String minPrcunt
) {

    /**
     * @param hilistCode 国家码
     * @param minPacCnt  包装数量
     * @param minPacunt  包装单位
     * @param minPrepunt 制剂单位
     * @param minPrcunt  计价单位
     * @return hint；国家码空时 null
     */
    public static NationalCatalogPackHint of(
        String hilistCode,
        String minPacCnt,
        String minPacunt,
        String minPrepunt,
        String minPrcunt) {
        if (isEmpty(hilistCode)) {
            return null;
        }
        return new NationalCatalogPackHint(
            hilistCode.trim(),
            blankToNull(minPacCnt),
            blankToNull(minPacunt),
            blankToNull(minPrepunt),
            blankToNull(minPrcunt));
    }

    /** 是否具备限价计价单位。 */
    public boolean hasPricingUnit() {
        return isNotEmpty(minPrcunt);
    }

    /**
     * 限价按包装单位本身（如盒价限价）：上传勿再折到片。
     */
    public boolean pricesByPackUnit() {
        if (isEmpty(minPrcunt) || isEmpty(minPacunt)) {
            return false;
        }
        return minPrcunt.trim().equals(minPacunt.trim());
    }

    /**
     * 解析最小包装数量；非法或 ≤0 时 null。
     */
    public BigDecimal minPacCntAsDecimal() {
        if (isEmpty(minPacCnt)) {
            return null;
        }
        try {
            BigDecimal n = new BigDecimal(minPacCnt.trim());
            if (n.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return n;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (isEmpty(value)) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
