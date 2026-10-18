/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.documentdetail.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 单据详情报表按品规汇总行（同筛选条件下对 supply 明细行聚合；仅进价，无售价）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情按品规汇总行")
public class DocumentDetailItemSummaryRowDto {

    /** 分桶键（itemTable|itemId）。 */
    @Schema(description = "维度键")
    private String dimensionKey;

    /** 汇总维度 code；本期固定 ITEM。 */
    @Schema(description = "汇总维度 code")
    private String summaryDimension;

    /** 维度展示名（品名）。 */
    @Schema(description = "维度名称（品名）")
    private String dimensionName;

    /** 物品表名。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品 itemId")
    private Long itemId;

    /** 院内编码。 */
    @Schema(description = "院内编码")
    private String productBusNo;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 包装单位数量合计。 */
    @Schema(description = "包装单位数量合计")
    private BigDecimal packageQuantity;

    /** 包装单位名称。 */
    @Schema(description = "包装单位名称")
    private String packageUnitName;

    /** 最小（拆零）单位数量合计。 */
    @Schema(description = "最小单位数量合计")
    private BigDecimal minUnitQuantity;

    /** 最小单位名称。 */
    @Schema(description = "最小单位名称")
    private String minUnitName;

    /**
     * 进价单价（取 supply 落库 {@code price}）；同桶多行单价不一致时为空。
     */
    @Schema(description = "进价单价")
    private BigDecimal price;

    /** 进价合计（同桶 supply {@code totalPrice} 代数加总）。 */
    @Schema(description = "进价合计")
    private BigDecimal totalPrice;

    /** 命中明细行数（内部统计，汇总 Tab 可不展示）。 */
    @Schema(description = "命中明细行数")
    private Long lineCount;
}
