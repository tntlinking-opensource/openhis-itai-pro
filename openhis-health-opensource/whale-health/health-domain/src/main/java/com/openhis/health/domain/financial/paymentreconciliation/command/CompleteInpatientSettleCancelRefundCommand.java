/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 住院取消结算收口命令。 */
@Getter
@Setter
public class CompleteInpatientSettleCancelRefundCommand {

    /** 原住院结算支付单主键。 */
    private Long originalPaymentId;
    /** 冲正/退费支付单主键。 */
    private Long reversePaymentId;
    /** 医保冲正结算凭证行。 */
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    /** 渠道/业务结果枚举编码。 */
    private String outcomeEnum;
    /** 取消结算收口账单时间。 */
    private LocalDateTime billDate;
}
