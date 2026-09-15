/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.recon.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 收银侧金额快照（来自 fin_payment_rec_detail 汇总）。
 */
@Data
@Accessors(chain = true)
public class WorkInjuryPaymentAmountSnapshot {

    private Long paymentId;

    /** 支付头 status_enum。 */
    private String paymentStatus;

    /** 明细 charge_amount 合计（对照结算 medfee_sumamt）。 */
    private BigDecimal paymentMedfee;

    /** pay_enum=INSURANCE_FUND 合计（对照 fund_pay_sumamt）。 */
    private BigDecimal paymentHifp;

    /** pay_enum=CASH 合计（对照 psn_cash_pay）。 */
    private BigDecimal paymentCash;
}
