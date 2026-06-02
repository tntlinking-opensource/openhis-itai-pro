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
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.domain.event.DomainEvent;
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
