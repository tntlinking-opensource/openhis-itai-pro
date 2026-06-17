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

package com.openhis.health.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 汇总单合并明细行（IF-IP-SUM-03；不含批号 BR-IP-SUM-010）。
 */
@Data
@Accessors(chain = true)
/** 汇总单合并明细行。 */
@Schema(description = "汇总单合并明细行")
public class NurseStationDispenseSummarySlipLineDto {

    @Schema(description = "供应请求行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long supplyRequestId;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String specificationText;

    /** 拆零汇总数量。 */
    @Schema(description = "拆零汇总数量")
    private BigDecimal itemQuantity;

    /** 拆零单位 code。 */
    @Schema(description = "拆零单位 code")
    private String unitCode;

    /** 拆零单位展示。 */
    @Schema(description = "拆零单位展示")
    private String unitCodeText;

    /** 界面展示数量文案（满包整数 + 不足一包拆零余量，如 1盒1片）。 */
    @Schema(description = "界面展示数量文案")
    private String displayQuantityText;

    /** 账单单价。 */
    @Schema(description = "账单单价")
    private BigDecimal unitPrice;

    /** 账单总价累加。 */
    @Schema(description = "账单总价累加")
    private BigDecimal totalPrice;
}
