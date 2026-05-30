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

import java.util.List;

/**
 * 退费受理窗口已进入事件。
 *
 * <p>触发时机：进入退费受理窗口后（批量操作，aggregateId 取首条流水标识）。</p>
 */
@Getter
@Setter
public class RefundProcessingWindowEnteredEvent extends DomainEvent {

    /** 支付流水标识列表（批量受理场景）。 */
    private List<Long> paymentIds;

    /** 就诊标识。 */
    private Long encounterId;

    /**
     * 无参构造（反序列化用）。
     */
    public RefundProcessingWindowEnteredEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param firstPaymentId 首条支付流水标识（用作 aggregateId）
     * @param paymentIds     支付流水标识列表
     * @param encounterId    就诊标识
     */
    public RefundProcessingWindowEnteredEvent(Long firstPaymentId, List<Long> paymentIds, Long encounterId) {
        super(firstPaymentId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.paymentIds = paymentIds;
        this.encounterId = encounterId;
    }
}
