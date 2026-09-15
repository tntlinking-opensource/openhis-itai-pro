/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.queries;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 医保费用明细四列金额 SQL 合计结果。
 */
@Data
@Accessors(chain = true)
public class InsuranceFeeDetailAmountSummary {

    /** 命中笔数。 */
    private long lineCount;

    /** 医疗费总额合计。 */
    private BigDecimal medfeeSumamtTotal = BigDecimal.ZERO;

    /** 基金支付总额合计。 */
    private BigDecimal fundPaySumamtTotal = BigDecimal.ZERO;

    /** 个人账户支付合计。 */
    private BigDecimal acctPayTotal = BigDecimal.ZERO;

    /** 现金支付合计。 */
    private BigDecimal psnCashPayTotal = BigDecimal.ZERO;
}
