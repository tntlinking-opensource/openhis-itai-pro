/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 完成门诊退费命令。 */
@Getter
@Setter
public class CompleteOutpatientRefundCommand {
    /** 退费支付单主键。 */
    private Long paymentId;
    /** 渠道/业务结果枚举编码。 */
    private String outcomeEnum;
    /** 医保冲正结算凭证行。 */
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    /** 退费完成账单时间。 */
    private LocalDateTime billDate;
}
