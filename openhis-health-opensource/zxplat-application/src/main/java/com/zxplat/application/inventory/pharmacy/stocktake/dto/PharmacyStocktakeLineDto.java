/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.stocktake.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药房盘点单写模型明细行（IF-PHA-ST-04/05）。
 *
 * <p>{@code profitLossQuantity} 映射持久化 {@code item_quantity}（盈亏，可负可零）；{@code actualQuantity} 映射 {@code total_quantity}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "药房盘点单明细行（写）")
public class PharmacyStocktakeLineDto {

    /** 物品来源表名。 */
    @Schema(description = "物品来源表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    /** 盈亏数量 profitLossQuantity。 */
    @Schema(description = "盈亏数量 profitLossQuantity", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal profitLossQuantity;

    /** 实盘数量 actualQuantity。 */
    @Schema(description = "实盘数量 actualQuantity")
    private BigDecimal actualQuantity;

    /** 实盘金额 actualAmount。 */
    @Schema(description = "实盘金额 actualAmount")
    private BigDecimal actualAmount;

    /** 盈亏金额 profitLossAmount。 */
    @Schema(description = "盈亏金额 profitLossAmount")
    private BigDecimal profitLossAmount;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 剩余追溯码 traceNo。 */
    @Schema(description = "剩余追溯码 traceNo")
    private String traceNo;
    /** 选品锚点库存行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库存项主键 inventoryItemId（选品，可选）。 */
    @Schema(description = "库存项主键 inventoryItemId（选品，可选）")
    private Long inventoryItemId;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal price;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String remake;

    /** 业务发生时间（制单时间）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 业务发生时间 occurrenceTime。 */
    @Schema(description = "业务发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;
}
