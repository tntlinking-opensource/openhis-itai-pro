/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import com.openhis.health.domain.share.enums.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 按支付主键回写状态与医保结算凭证。 */
@Getter
@Setter
public class UpdatePaymentStatusWithSettleIdsCommand {
    /** 支付单主键 {@code fin_payment.id}。 */
    private Long paymentId;
    /** 目标支付状态。 */
    private PaymentStatus statusEnum;
    /** 待写入/更新的医保结算凭证行。 */
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    /** 渠道/业务结果枚举编码（可空，与状态联动回写）。 */
    private String outcomeEnum;
}
