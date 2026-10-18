/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 支付前中止预结算命令：在正式扣款前撤销尚未完成的预结算快照，避免重复结算。
 */
@Getter
@Setter
public class AbortPreSettlementBeforePaymentCommand {
    /** 待中止预结算的支付单主键 {@code fin_payment.id}。 */
    private Long paymentId;
}
