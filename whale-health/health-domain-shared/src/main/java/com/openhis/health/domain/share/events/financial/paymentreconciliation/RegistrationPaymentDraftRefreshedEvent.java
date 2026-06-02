/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.financial.paymentreconciliation;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
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
