/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 确认支付明细回执命令：将渠道回执明细落库到指定支付单，完成明细闭环。
 */
@Getter
@Setter
/**
 * FinalizePaymentDetailReceiptCommand。
 */
public class FinalizePaymentDetailReceiptCommand {
    /** 支付单主键。 */
    /**
     * paymentId。
     */
    private Long paymentId;
    /** 回执明细分录（与 PaymentDetailItemCommand 结构一致）。 */
    /**
     * payReceiptDetails。
     */
    private List<PaymentDetailItemCommand> payReceiptDetails;
}
