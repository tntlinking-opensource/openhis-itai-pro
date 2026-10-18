/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 门诊费用明细金额合计读模型（DM-ADM-011 §6.2.26；域层为账单事实代数和，退负/医保由应用层装配）。
 */
@Getter
@Setter
public class OutpatientFeeDetailAmountSummaryQueryModel {

    /** 原价合计。 */
    private BigDecimal baseAmountTotal = BigDecimal.ZERO;

    /** 优惠合计（base − receivable 的域层近似，应用层可重算）。 */
    private BigDecimal discountAmountTotal = BigDecimal.ZERO;

    /** 应收合计（discount_amount 回退 total_price）。 */
    private BigDecimal receivableAmountTotal = BigDecimal.ZERO;

    /** 折后合计（system_discount_price 回退链）。 */
    private BigDecimal discountedAmountTotal = BigDecimal.ZERO;

    /** 行金额合计（total_price）。 */
    private BigDecimal lineAmountTotal = BigDecimal.ZERO;
}
