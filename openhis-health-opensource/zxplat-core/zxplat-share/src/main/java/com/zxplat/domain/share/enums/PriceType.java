/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import lombok.Getter;

/**
 * 定价类型枚举。
 *
 * <p>存储口径：字符串编码（与定价聚合保持一致）。</p>
 */
@Getter
public enum PriceType implements EnumInterface {
/** 零售价（PriceType，编码 {@code SALE}）。 */
    SALE(0L, "SALE", "零售价", "", 1L),
/** 采购进价（PriceType，编码 {@code PURCHASE}）。 */
    PURCHASE(0L, "PURCHASE", "采购进价", "", 2L),
/** 限价（PriceType，编码 {@code LIMIT}）。 */
    LIMIT(0L, "LIMIT", "限价", "", 3L),
/** 批号进价（PriceType，编码 {@code LOT_NUMBER_COST}）。 */
    LOT_NUMBER_COST(0L, "LOT_NUMBER_COST", "批号进价", "", 4L),
/** 批号售价（PriceType，编码 {@code LOT_NUMBER_PRICE}）。 */
    LOT_NUMBER_PRICE(0L, "LOT_NUMBER_PRICE", "批号售价", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PriceType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 处理String code逻辑。
     * @return 返回结果
     */
    public static boolean isLotNumberPriceType(String code) {
        if (code == null) {
            return false;
        }
        return LOT_NUMBER_COST.getCode().equals(code) || LOT_NUMBER_PRICE.getCode().equals(code);
    }

}
