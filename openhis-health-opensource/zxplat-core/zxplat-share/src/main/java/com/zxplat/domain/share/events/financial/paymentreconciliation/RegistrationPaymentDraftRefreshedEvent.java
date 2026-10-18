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
 * 挂号收费草稿已刷新事件。
 *
 * <p>触发时机：挂号收费草稿刷新后。</p>
 */
@Getter
@Setter
public class RegistrationPaymentDraftRefreshedEvent extends DomainEvent {

    /** 患者标识。 */
    private Long patientId;

    /**
     * 无参构造（反序列化用）。
     */
    public RegistrationPaymentDraftRefreshedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId 支付流水标识
     * @param patientId   患者标识
     */
    public RegistrationPaymentDraftRefreshedEvent(Long aggregateId, Long patientId) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.patientId = patientId;
    }
}
