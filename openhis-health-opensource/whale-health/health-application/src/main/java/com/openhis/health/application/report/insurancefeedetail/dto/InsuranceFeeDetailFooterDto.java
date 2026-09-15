/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.insurancefeedetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 医保费用明细报表页脚四列合计（同筛选全量，非当前页）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医保费用明细报表页脚")
public class InsuranceFeeDetailFooterDto {

    /** 同筛选命中笔数。 */
    @Schema(description = "命中笔数")
    private Long lineCount;

    /** 医疗费总额合计（结算 2 位）。 */
    @Schema(description = "医疗费总额合计")
    private BigDecimal medfeeSumamtTotal;

    /** 基金支付总额合计。 */
    @Schema(description = "基金支付总额合计")
    private BigDecimal fundPaySumamtTotal;

    /** 个人账户支付合计。 */
    @Schema(description = "个人账户支付合计")
    private BigDecimal acctPayTotal;

    /** 现金支付合计。 */
    @Schema(description = "现金支付合计")
    private BigDecimal psnCashPayTotal;
}
