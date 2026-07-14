/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.costdetail.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 费用汇总行 DTO（IF-IP-CDT-02，presentationMode=SUMMARY）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院护士站费用汇总行")
public class NurseStationCostSummaryLineDto {

    /** 产品 id（分组键）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品 id（分组键）")
    private Long productId;

    /** 产品表名。 */
    @Schema(description = "产品表名")
    private String productTableName;

    /** 产品名称。 */
    @Schema(description = "产品名称")
    private String itemName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String itemSpec;

    /** 分类 code。 */
    @Schema(description = "分类 code")
    private String categoryCode;

    /** 分类展示名。 */
    @Schema(description = "分类展示名")
    private String categoryDisplay;

    /** 展示单价。 */
    @Schema(description = "展示单价")
    private BigDecimal unitPrice;

    /** 行合计净额。 */
    @Schema(description = "行合计净额")
    private BigDecimal lineTotalAmount;

    /** 满包装整数数量。 */
    @Schema(description = "满包装整数数量")
    private Integer packageQuantity;

    /** 拆零余量。 */
    @Schema(description = "拆零余量")
    private BigDecimal splitUnitQuantity;

    /** 包装单位 code。 */
    @Schema(description = "包装单位 code")
    private String packageUnitCode;

    /** 包装单位展示名。 */
    @Schema(description = "包装单位展示名")
    private String packageUnitDisplay;

    /** 拆零单位 code。 */
    @Schema(description = "拆零单位 code")
    private String splitUnitCode;

    /** 拆零单位展示名。 */
    @Schema(description = "拆零单位展示名")
    private String splitUnitDisplay;

    /** 费用大类块 code。 */
    @Schema(description = "费用大类块 code")
    private String expenseBlockCode;

    /** 费用大类块展示名。 */
    @Schema(description = "费用大类块展示名")
    private String expenseBlockDisplay;
}
