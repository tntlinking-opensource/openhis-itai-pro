/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 刷新挂号支付草稿命令：在挂号场景下按最新费用明细与金额重算支付草稿。
 */
@Getter
@Setter
public class RefreshRegistrationPaymentDraftCommand {
    /** 支付单主键。 */
    private Long paymentId;
    /** 费用明细 ID 集合序列化串。 */
    private String chargeItemIds;
    /** 实收金额。 */
    private BigDecimal tenderedAmount;
    /** 展示金额。 */
    private BigDecimal displayAmount;
    /** 支付明细分录。 */
    private List<PaymentDetailItemCommand> paymentDetails;
}
