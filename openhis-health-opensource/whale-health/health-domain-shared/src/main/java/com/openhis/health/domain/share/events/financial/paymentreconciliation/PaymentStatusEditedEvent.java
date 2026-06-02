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
 * 支付状态已变更事件。
 *
 * <p>触发时机：支付状态和结算号回写后。</p>
 */
@Getter
@Setter
public class PaymentStatusEditedEvent extends DomainEvent {

    /** 变更前状态码。 */
    private PaymentStatus beforeStatusEnum;

    /** 变更后状态码。 */
    private PaymentStatus afterStatusEnum;

    /** 医保结算标识集合（JSON 数组字符串，可选）。 */
    private String ybSettleIds;

    /**
     * 无参构造（反序列化用）。
     */
    public PaymentStatusEditedEvent() {
        super();
    }

    /**
     * 全参构造。
     *
     * @param aggregateId      支付流水标识
     * @param beforeStatusEnum 变更前状态码
     * @param afterStatusEnum  变更后状态码
     * @param ybSettleIds      医保结算标识集合
     */
    public PaymentStatusEditedEvent(Long aggregateId, PaymentStatus beforeStatusEnum,
                                    PaymentStatus afterStatusEnum, String ybSettleIds) {
        super(aggregateId, AggregateTypeConstants.PAYMENT_RECONCILIATION);
        this.beforeStatusEnum = beforeStatusEnum;
        this.afterStatusEnum = afterStatusEnum;
        this.ybSettleIds = ybSettleIds;
    }
}
