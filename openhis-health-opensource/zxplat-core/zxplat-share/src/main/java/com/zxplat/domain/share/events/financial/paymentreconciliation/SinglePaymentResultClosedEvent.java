/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.financial.paymentreconciliation;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 单笔支付结果已收口事件。
 *
 * <p>触发时机：单笔支付结果收口后。</p>
 */
@Getter
@Setter
public class SinglePaymentResultClosedEvent extends DomainEvent {

    /** 最终状态码。 */
    private PaymentStatus finalStatusEnum;

    /**
     * 无参构造（反序列化用）。
     */
    public SinglePaymentResultClosedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId     支付流水标识
     * @param finalStatusEnum 最终状态码
     */
    public SinglePaymentResultClosedEvent(Long aggregateId, PaymentStatus finalStatusEnum) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.finalStatusEnum = finalStatusEnum;
    }
}
