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
 * 住院结算已取消事件。
 *
 * <p>触发时机：取消住院结算完成后。</p>
 */
@Getter
@Setter
public class InpatientSettleCancelledEvent extends DomainEvent {

    /** 关联原单标识。 */
    private Long relationId;

    /** 就诊标识。 */
    private Long encounterId;
    /**
     * 撤销金额（用于日结回冲收入）。
     * 向后兼容：可通过 setter 注入。
     */
    private java.math.BigDecimal cancelledAmount;
    /**
     * 收费员标识（用于日结分组）。
     * 向后兼容：可通过 setter 注入。
     */
    private Long entererId;
    /**
     * 撤销时间（用于日结日期归属）。
     * 向后兼容：可通过 setter 注入。
     */
    private java.time.LocalDateTime cancelTime;

    /** 无参构造（反序列化用）。 */
    public InpatientSettleCancelledEvent() {
        super();
    }

    /**
     * 向后兼容构造器；cancelledAmount / entererId / cancelTime 通过 setter 注入。
     *
     * @param aggregateId 支付流水标识
     * @param relationId  关联原单标识
     * @param encounterId 就诊标识
     */
    public InpatientSettleCancelledEvent(Long aggregateId, Long relationId, Long encounterId) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.relationId = relationId;
        this.encounterId = encounterId;
    }
}
