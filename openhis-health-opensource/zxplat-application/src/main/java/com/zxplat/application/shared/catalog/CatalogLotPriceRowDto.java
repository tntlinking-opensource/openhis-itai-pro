/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 药品/耗材目录批号维真实价格行：进价 {@code LOT_NUMBER_COST}、售价 {@code LOT_NUMBER_PRICE}。
 *
 * <p>与目录维护态 {@code PURCHASE}/{@code SALE}（采购参考价/销售参考价）分离；金额均为每包装单位当前 ACTIVE 价。</p>
 */
@Data
@Schema(description = "目录批号进价/售价行")
public class CatalogLotPriceRowDto {

    /** 批号（定价行 {@code lot_no}）。 */
    @Schema(description = "批号", example = "20250101")
    private String lotNumber;

    /**
     * 批号进价（元/包装单位）。
     *
     * <p>价类 {@code LOT_NUMBER_COST}：进货入库或调价写入的真实进价，非目录采购参考价。</p>
     */
    @Schema(description = "批号进价（元/包装单位，LOT_NUMBER_COST）", example = "10.50")
    private BigDecimal costPrice;

    /**
     * 批号售价（元/包装单位）。
     *
     * <p>价类 {@code LOT_NUMBER_PRICE}：门诊/收费实际售卖价，非目录销售参考价。</p>
     */
    @Schema(description = "批号售价（元/包装单位，LOT_NUMBER_PRICE）", example = "15.80")
    private BigDecimal salePrice;
}
