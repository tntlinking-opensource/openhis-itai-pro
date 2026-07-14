/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 住院支付结算落账命令。 */
@Getter
@Setter
public class SettleInpatientPaymentCommand {
    /** 支付单主键 {@code fin_payment.id}。 */
    private Long paymentId;
    /** 渠道/业务结果枚举编码（与收银台 outcome 一致）。 */
    private String outcomeEnum;
    /** 本轮落账关联的医保结算凭证行。 */
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    /** 账单业务时间（住院结算日）。 */
    private LocalDateTime billDate;
}
