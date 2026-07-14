/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 清算金额汇总出参（门诊 + 住院结算快照合计） */
@Data
@Accessors(chain = true)
public class ClearAmountSummaryResult {

    private BigDecimal medfeeSumamt;
    private BigDecimal medSumfee;
    private BigDecimal fundAppySum;
    private BigDecimal cashPayamt;
    private BigDecimal acctPay;
    private Integer setlCount;
}
