/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2206 完成后刷新收银草稿应付金额结果。 */
@Data
@Accessors(chain = true)
public class RefreshWorkInjuryPreSettleAmountsResult {

    private BigDecimal displayAmount;
    private String ybChrgBchno;
    private BigDecimal ybMedfeeSumamt;
    private BigDecimal ybFundPaySumamt;
    private BigDecimal ybAcctPay;
    private BigDecimal ybPsnCashPay;
}
