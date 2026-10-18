/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 调价批号明细 DTO（物流多批号 / 诊疗一对一调前价行）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调价批号明细")
public class PriceAdjustmentDetailDto {

    /** 子表主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "子表主键")
    private Long id;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 快照数量。 */
    @Schema(description = "快照数量")
    private BigDecimal quantity;

    /** 调前进价。 */
    @Schema(description = "调前进价")
    private BigDecimal oldPurchaseAmount;

    /** 调前售价。 */
    @Schema(description = "调前售价")
    private BigDecimal oldSellingAmount;

    /** 进价盈亏。 */
    @Schema(description = "进价盈亏")
    private BigDecimal purchaseImpact;

    /** 售价盈亏。 */
    @Schema(description = "售价盈亏")
    private BigDecimal sellingImpact;
}
