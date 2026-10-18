/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 退号联动退费完成命令。 */
@Getter
@Setter
public class CompleteRegistrationCancelRefundCommand {

    /** 原挂号支付单主键。 */
    private Long originalPaymentId;
    /** 冲正/退费支付单主键。 */
    private Long reversePaymentId;
    /** 渠道/业务结果枚举编码。 */
    private String outcomeEnum;
    /** 医保冲正结算凭证行。 */
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    /** 退费完成账单时间。 */
    private LocalDateTime billDate;
}
