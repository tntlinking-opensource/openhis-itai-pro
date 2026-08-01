/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.pharmacydailysettlement.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 药房日结主区品规汇总行（DD §2.8.1：同药且进/售单价一致才合并；净 0 仍展示）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药房日结品规汇总行")
public class PharmacyDailySettlementProductRowDto {

    /** 药品主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品主键")
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 药品目录业务编码 bus_no。 */
    @Schema(description = "药品目录 bus_no")
    private String busNo;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 生产厂家。 */
    @Schema(description = "生产厂家")
    private String manufacturer;

    /** 净数量（已发 − 已退；一发一退可为 0）。 */
    @Schema(description = "净数量")
    private BigDecimal netQuantity;

    /** 进价单价（分桶键；同桶内一致）。 */
    @Schema(description = "进价单价")
    private BigDecimal purchaseUnitPrice;

    /** 售价单价（分桶键；同桶内一致）。 */
    @Schema(description = "售价单价")
    private BigDecimal saleUnitPrice;

    /** 进价总额（净）。 */
    @Schema(description = "进价总额（净）")
    private BigDecimal purchaseAmount;

    /** 售价总额（净）。 */
    @Schema(description = "售价总额（净）")
    private BigDecimal saleAmount;
}
