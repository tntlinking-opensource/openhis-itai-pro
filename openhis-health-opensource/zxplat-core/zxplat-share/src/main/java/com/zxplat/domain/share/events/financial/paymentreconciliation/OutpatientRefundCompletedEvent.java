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

import java.math.BigDecimal;

/**
 * 门诊退费已完成事件。
 *
 * <p>触发时机：门诊退款完成后。</p>
 */
@Getter
@Setter
public class OutpatientRefundCompletedEvent extends DomainEvent {

    /** 关联原单标识。 */
    private Long relationId;

    /** 就诊标识。 */
    private Long encounterId;

    /** 退费金额。 */
    private BigDecimal displayAmount;

    /** 退费后目标状态（REFUND_ALL/REFUND_PART 对应的状态码）。 */
    private PaymentStatus afterStatusEnum;
    /**
     * 收费员标识（用于日结汇总分组）。
     * 向后兼容：可通过 setter 注入；旧构造器调用不受影响。
     */
    private Long entererId;
    /**
     * 退费时间（用于日结汇总日期归属）。
     * 向后兼容：可通过 setter 注入；旧构造器调用不受影响。
     */
    private java.time.LocalDateTime refundTime;

    /** 无参构造（反序列化用）。 */
    public OutpatientRefundCompletedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；entererId / refundTime 通过 setter 注入。
     *
     * @param aggregateId     支付流水标识
     * @param relationId      关联原单标识
     * @param encounterId     就诊标识
     * @param displayAmount   退费金额
     * @param afterStatusEnum 退费类型（全额/部分对应的状态码）
     */
    public OutpatientRefundCompletedEvent(Long aggregateId, Long relationId, Long encounterId,
                                          BigDecimal displayAmount, PaymentStatus afterStatusEnum) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.relationId = relationId;
        this.encounterId = encounterId;
        this.displayAmount = displayAmount;
        this.afterStatusEnum = afterStatusEnum;
    }
}
