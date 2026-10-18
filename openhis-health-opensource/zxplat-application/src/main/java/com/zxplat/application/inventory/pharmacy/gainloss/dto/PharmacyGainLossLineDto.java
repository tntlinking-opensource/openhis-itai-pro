/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.gainloss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药房损益单写模型明细行（IF-PHA-GL-04/05）。
 *
 * <p>{@code gainLossQuantity} 映射持久化 {@code item_quantity}（盈亏：正报溢、负报损，禁止 0）；
 * {@code actualQuantity} 映射 {@code total_quantity}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "药房损益单明细行（写）")
public class PharmacyGainLossLineDto {

    /** 物品来源表名。 */
    @Schema(description = "物品来源表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    /** 盈亏数量（正数报溢、负数报损，禁止 0）；映射 {@code item_quantity}。 */
    @Schema(description = "盈亏数量（正报溢/负报损，禁止0）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal gainLossQuantity;

    /** 实盘数量 actualQuantity。 */
    @Schema(description = "实盘数量 actualQuantity")
    private BigDecimal actualQuantity;

    /** 实盘金额 actualAmount。 */
    @Schema(description = "实盘金额 actualAmount")
    private BigDecimal actualAmount;

    /** 盈亏金额 gainLossAmount。 */
    @Schema(description = "盈亏金额 gainLossAmount")
    private BigDecimal gainLossAmount;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 损益扫码 traceNo（报溢 IN / 报损 OUT，与盈亏正负同向）。 */
    @Schema(description = "损益扫码 traceNo（与盈亏正负同向直接加减）")
    private String traceNo;
    /** 库存项主键 inventoryItemId（选品，可选）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存项主键 inventoryItemId（选品，可选）")
    private Long inventoryItemId;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal price;

    /** 变更原因 reason。 */
    @Schema(description = "变更原因 reason")
    private String reason;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String remake;

    /** 业务发生时间 occurrenceTime。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;
}
