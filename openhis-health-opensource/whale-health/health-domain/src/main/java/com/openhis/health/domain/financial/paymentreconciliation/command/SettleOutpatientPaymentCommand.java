/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 门诊支付结算落账命令。 */
@Getter
@Setter
public class SettleOutpatientPaymentCommand {
    /** 支付单主键 {@code fin_payment.id}。 */
    private Long paymentId;
    /** 渠道/业务结果枚举编码（与收银台 outcome 一致）。 */
    private String outcomeEnum;
    /** 本轮落账关联的医保结算凭证行。 */
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    /** 账单业务时间（门诊结算日）。 */
    private LocalDateTime billDate;
    /** 实收渠道回执明细分录（现金/聚合支付等）。 */
    private List<PaymentDetailItemCommand> payReceiptDetails;
    /**
     * 患者应付现金（2207 Σ psn_cash + 自费段）；非空时覆盖头表 {@code display_amount}/{@code tendered_amount}。
     *
     * <p>避免多批次正式结算后仍沿用 2206 草稿现金导致渠道与 fin_type 勾稽差。</p>
     */
    private BigDecimal displayAmount;
    /** 与 {@link #displayAmount} 同值写入 tendered；可空表示不改头表。 */
    private BigDecimal tenderedAmount;
}
