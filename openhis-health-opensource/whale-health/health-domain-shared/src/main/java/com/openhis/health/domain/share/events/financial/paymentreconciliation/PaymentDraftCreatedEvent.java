/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.financial.paymentreconciliation;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 支付草稿已创建事件。
 *
 * <p>触发时机：预结算生成支付草稿后。</p>
 */
@Getter
@Setter
public class PaymentDraftCreatedEvent extends DomainEvent {

    /** 就诊标识。 */
    private Long encounterId;

    /** 患者标识。 */
    private Long patientId;

    /** 收费明细标识集合（JSON 数组字符串）。 */
    private String chargeItemIds;

    /** 应收金额。 */
    private BigDecimal displayAmount;

    /**
     * 无参构造（反序列化用）。
     */
    public PaymentDraftCreatedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId   支付流水标识
     * @param encounterId   就诊标识
     * @param patientId     患者标识
     * @param chargeItemIds 收费明细标识集合
     * @param displayAmount 应收金额
     */
    public PaymentDraftCreatedEvent(Long aggregateId, Long encounterId, Long patientId,
                                    String chargeItemIds, BigDecimal displayAmount) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.chargeItemIds = chargeItemIds;
        this.displayAmount = displayAmount;
    }
}
