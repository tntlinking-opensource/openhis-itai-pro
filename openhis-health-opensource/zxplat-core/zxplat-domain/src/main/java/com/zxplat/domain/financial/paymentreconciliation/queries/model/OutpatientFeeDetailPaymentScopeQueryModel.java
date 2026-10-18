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
 * 门诊费用明细：单条 payment 读侧 scope（DM-FIN-002 §6.2.17；快照截断补全编排用）。
 *
 * <p>由 {@link com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService}
 * 在 bill_date 筛选后写入 {@link com.zxplat.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery}，
 * 供 {@link com.zxplat.domain.application.outpatient.financial.feedetail.support.OutpatientFeeDetailLoader} 补全
 * {@code charge_item_ids}  varchar 截断漏挂的已结行。</p>
 */
@Getter
@Setter
public class OutpatientFeeDetailPaymentScopeQueryModel {

    /** 支付流水主键。 */
    private Long paymentId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 结算业务时间（payment.bill_date）。 */
    private LocalDateTime billDate;

    /** 收费人（payment.enterer_id）。 */
    private Long cashierId;

    /** 是否退费 payment（status=REFUNDED）。 */
    private Boolean refundPayment;

    /** 持久化的 charge_item_ids 快照原文（用于识别 varchar 截断）。 */
    private String chargeItemIdsSnapshot;

    /** 已确认医保结算的基金+个账合计（无医保时为 0）。 */
    private BigDecimal ybFundAndAcctTotal;
}
