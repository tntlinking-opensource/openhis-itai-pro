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
