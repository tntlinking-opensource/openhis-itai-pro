/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 2206 预结算快照摘要（写入 {@code yb_wi_pre_settle}，供 2207 与收银展示）。
 */
@Data
@Accessors(chain = true)
public class WorkInjuryPreSettleSnapshot {

    /** 收费批次号。 */
    private String chrgBchno;
    /** 医疗费总额 medfee_sumamt。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额 fund_pay_sumamt。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出 acct_pay。 */
    private BigDecimal acctPay;
    /** 个人现金支出 psn_cash_pay。 */
    private BigDecimal psnCashPay;
}
