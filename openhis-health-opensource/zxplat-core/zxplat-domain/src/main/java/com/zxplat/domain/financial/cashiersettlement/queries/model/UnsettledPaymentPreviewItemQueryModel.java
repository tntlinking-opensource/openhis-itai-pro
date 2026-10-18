/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 未交账 payment 预览行。
 */
@Getter
@Setter
public class UnsettledPaymentPreviewItemQueryModel {

    /** 支付流水 ID。 */
    private Long paymentId;

    /** 支付单号。 */
    private String paymentNo;

    /** 业务 kind code。 */
    private String kindEnum;

    /** 支付状态 code。 */
    private String statusEnum;

    /** 收费员（enterer）。 */
    private Long entererId;

    /** 结算时间。 */
    private LocalDateTime billDate;

    /** 收退方向：PAY / UN_PAY。 */
    private String paymentEnum;

    /** 逆向单关联原支付主键；非空表示退费/反结逆向流水。 */
    private Long relationId;

    /** 实收金额。 */
    private BigDecimal tenderedAmount;

    /** 退还金额。 */
    private BigDecimal returnedAmount;

    /** 净额。 */
    private BigDecimal netAmount;

    /** 费用性质合同业务号（{@code fin_payment_reconciliation.contract_no}）。 */
    private String contractNo;
}
