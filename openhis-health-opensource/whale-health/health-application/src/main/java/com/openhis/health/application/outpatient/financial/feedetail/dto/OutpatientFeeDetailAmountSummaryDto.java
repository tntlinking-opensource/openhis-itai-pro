/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.feedetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 门诊费用明细金额合计 DTO（就诊小计与全局 summary 共用结构）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊费用明细金额合计")
public class OutpatientFeeDetailAmountSummaryDto {

    /** 原价合计。 */
    @Schema(description = "原价合计")
    private BigDecimal baseAmountTotal;

    /** 优惠金额合计。 */
    @Schema(description = "优惠金额合计")
    private BigDecimal discountAmountTotal;

    /** 应收金额合计。 */
    @Schema(description = "应收金额合计")
    private BigDecimal receivableAmountTotal;

    /** 折后金额合计。 */
    @Schema(description = "折后金额合计")
    private BigDecimal discountedAmountTotal;

    /** 行金额合计（含退单负号）。 */
    @Schema(description = "行金额合计")
    private BigDecimal lineAmountTotal;

    /** 实收金额合计（含退单负号）。 */
    @Schema(description = "实收金额合计")
    private BigDecimal actualReceivedAmountTotal;

    /** 医保金额合计（含退单负号）。 */
    @Schema(description = "医保金额合计")
    private BigDecimal insuranceAmountTotal;

    /** 自费金额合计（含退单负号）。 */
    @Schema(description = "自费金额合计")
    private BigDecimal selfPayAmountTotal;
}
