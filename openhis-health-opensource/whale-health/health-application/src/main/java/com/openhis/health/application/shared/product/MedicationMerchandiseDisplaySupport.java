/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.whale.core.application.util.StringUtils;

/**
 * 药品展示名拼装：通用名后括号附商品名（页面查处方 / 发药笺等；打印勿用本类）。
 *
 * <p>规则：有非空商品名且与通用名不同时拼 {@code 通用名(商品名)}；无商品名时不拼空括号。</p>
 */
public final class MedicationMerchandiseDisplaySupport {

    private MedicationMerchandiseDisplaySupport() {
    }

    /**
     * 拼药品页面展示名：有商品名则括号追加，无则仅通用名。
     *
     * @param productName     通用名 / 目录名称；可空
     * @param merchandiseName 商品名；空或与通用名相同则不拼括号段
     * @return 展示串；两段皆空时返回空串
     */
    public static String formatDisplayName(String productName, String merchandiseName) {
        String name = StringUtils.trimToEmpty(productName);
        String merchandise = StringUtils.trimToEmpty(merchandiseName);
        // 无商品名：不拼空括号
        if (StringUtils.isEmpty(merchandise)) {
            return name;
        }
        // 仅有商品名：直接回商品名，避免 "()商品"
        if (StringUtils.isEmpty(name)) {
            return merchandise;
        }
        // 商品名与通用名相同：不重复括号
        if (merchandise.equals(name)) {
            return name;
        }
        return name + "(" + merchandise + ")";
    }
}
