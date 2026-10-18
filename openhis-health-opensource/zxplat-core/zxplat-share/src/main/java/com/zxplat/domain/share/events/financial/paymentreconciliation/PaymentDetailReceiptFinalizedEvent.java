/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.financial.paymentreconciliation;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 支付回执明细已收口事件。
 *
 * <p>触发时机：支付回执明细收口后。</p>
 */
@Getter
@Setter
public class PaymentDetailReceiptFinalizedEvent extends DomainEvent {

    /** 回执明细数量。 */
    private Integer detailCount;
    /**
     * 收费员标识（用于日结分组）。
     * 向后兼容：可通过 setter 注入。
     */
    private Long entererId;
    /**
     * 收口时间（用于日结日期归属）。
     * 向后兼容：可通过 setter 注入。
     */
    private java.time.LocalDateTime finalizedTime;

    /** 无参构造（反序列化用）。 */
    public PaymentDetailReceiptFinalizedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；entererId / finalizedTime 通过 setter 注入。
     *
     * @param aggregateId 支付流水标识
     * @param detailCount 回执明细数量
     */
    public PaymentDetailReceiptFinalizedEvent(Long aggregateId, Integer detailCount) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.detailCount = detailCount;
    }
}
