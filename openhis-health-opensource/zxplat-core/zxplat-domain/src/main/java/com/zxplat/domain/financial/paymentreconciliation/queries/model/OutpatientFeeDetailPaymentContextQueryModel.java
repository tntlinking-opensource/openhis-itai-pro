/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门诊费用明细：单条 chargeItem 关联的 payment 读上下文（DM-FIN-002 §6.2.17～6.2.18；AS-OP-FIN-005 **BR-OP-FDT-011**）。
 */
@Getter
@Setter
public class OutpatientFeeDetailPaymentContextQueryModel {

    /** 收费明细主键。 */
    private Long chargeItemId;

    /** 支付流水主键。 */
    private Long paymentId;

    /** 就诊主键（payment.encounter_id）。 */
    private Long encounterId;

    /** 结算业务时间（payment.bill_date）。 */
    private LocalDateTime billDate;

    /** 收费人（payment.enterer_id）。 */
    private Long cashierId;

    /** 是否退费 payment（status=REFUNDED）。 */
    private Boolean refundPayment;

    /**
     * 本 payment 快照内各 chargeItem {@code |total_price|} 之和（医保占比分母）。
     */
    private BigDecimal paymentSnapshotAbsAmountTotal;

    /**
     * 已确认医保结算的基金+个账合计（无医保时为 0）。
     */
    private BigDecimal ybFundAndAcctTotal;
}
