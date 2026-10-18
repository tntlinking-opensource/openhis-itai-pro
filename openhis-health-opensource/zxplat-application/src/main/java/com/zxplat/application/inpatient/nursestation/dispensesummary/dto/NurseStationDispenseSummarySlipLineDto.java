/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.dto;

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
@Schema(description = "汇总单合并明细行")
public class NurseStationDispenseSummarySlipLineDto {

    @Schema(description = "供应请求行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 供应请求行主键。 */
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

    /**
     * 界面数量文案（BR-IP-SUM-015）：输液一律拆零直显；非输液恰为整包装时包装单位凑整
     * （如 {@code 1盒}），否则拆零直显；禁止满包+余量拼接。
     */
    @Schema(description = "界面展示数量文案（输液拆零；非输液整包装凑整）")
    private String displayQuantityText;

    /** 账单单价。 */
    @Schema(description = "账单单价")
    private BigDecimal unitPrice;

    /** 账单总价累加。 */
    @Schema(description = "账单总价累加")
    private BigDecimal totalPrice;
}
