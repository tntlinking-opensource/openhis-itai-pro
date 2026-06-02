/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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

    /** 分组键：父项 product id。 */
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

    /** 组内任一条 unit_price（BR-IP-CDT-012）。 */
    @Schema(description = "展示单价")
    private BigDecimal unitPrice;

    /** 组内 total_price 净额合计。 */
    @Schema(description = "行合计净额")
    private BigDecimal lineTotalAmount;

    /** 满包装整数数量（BR-IP-CDT-011）。 */
    @Schema(description = "满包装整数数量")
    private Integer packageQuantity;

    /** 不足一包时小单位数量；满包可为 null。 */
    @Schema(description = "拆零余量")
    private BigDecimal splitUnitQuantity;

    /** 包装单位 code。 */
    @Schema(description = "包装单位 code")
    private String packageUnitCode;

    /** 包装单位展示名。 */
    @Schema(description = "包装单位展示名")
    private String packageUnitDisplay;

    /** 拆零/小单位 code。 */
    @Schema(description = "拆零单位 code")
    private String splitUnitCode;

    /** 拆零/小单位展示名。 */
    @Schema(description = "拆零单位展示名")
    private String splitUnitDisplay;

    /** 费用大类块 code。 */
    @Schema(description = "费用大类块 code")
    private String expenseBlockCode;

    /** 费用大类块展示名。 */
    @Schema(description = "费用大类块展示名")
    private String expenseBlockDisplay;
}
