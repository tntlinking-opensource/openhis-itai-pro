/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.financial.paymentreconciliation;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 门诊支付已结算事件。
 *
 * <p>触发时机：门诊结算成功后。本事件仅承载支付流水、就诊锚点与金额等收银语义，
 * 不包含就诊生命周期状态；挂号缴费后的就诊状态迁移由就诊域 {@code OutpatientRegistrationConfirmedEvent} 驱动读模型。</p>
 */
@Getter
@Setter
public class OutpatientPaymentSettledEvent extends DomainEvent {

    /** 就诊标识。 */
    private Long encounterId;

    /** 收费明细标识集合（JSON 数组字符串）。 */
    private String chargeItemIds;

    /** 应收金额。 */
    private BigDecimal displayAmount;

    /** 结算时间。 */
    private LocalDateTime billDate;

    /** 收费员标识。 */
    private Long entererId;

    /** 医保结算凭证行。 */
    private List<PaymentYbSettlementEventItem> ybSettlements;

    /**
     * 无参构造（反序列化用）。
     */
    public OutpatientPaymentSettledEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId   支付流水标识
     * @param encounterId   就诊标识
     * @param chargeItemIds 收费明细标识集合
     * @param displayAmount 应收金额
     * @param billDate      结算时间
     * @param entererId     收费员标识
     */
    public OutpatientPaymentSettledEvent(Long aggregateId, Long encounterId, String chargeItemIds,
                                         BigDecimal displayAmount, LocalDateTime billDate, Long entererId) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.encounterId = encounterId;
        this.chargeItemIds = chargeItemIds;
        this.displayAmount = displayAmount;
        this.billDate = billDate;
        this.entererId = entererId;
    }
}
