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

package com.openhis.health.domain.share.events.financial.cashiersettlement;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 收费员交账提交成功事件。
 */
@Getter
@Setter
public class CashierSettlementSubmittedEvent extends DomainEvent {

    /** 交账单号。 */
    private String busNo;

    /** 交账类型：OUTPATIENT / INPATIENT。 */
    private String typeEnum;

    /** 交账经办人。 */
    private Long cashierId;

    /** 交账所属科室。 */
    private Long orgId;

    /** 交账提交时刻。 */
    private LocalDateTime settledAt;

    /** 本次挂载 payment 条数。 */
    private Integer paymentCount;

    /** 操作人（审计）。 */
    private Long operatorId;

    /**
     * 无参构造（反序列化用）。
     */
    public CashierSettlementSubmittedEvent() {
        super();
    }

    /**
     * 构造交账提交事件载荷。
     *
     * @param settlementId 交账头主键
     * @param busNo        交账单号
     * @param typeEnum     交账类型 code
     * @param cashierId    经办人
     * @param orgId        科室
     * @param settledAt    交账时刻
     * @param paymentCount 挂载条数
     * @param operatorId   操作人（审计）
     */
    public CashierSettlementSubmittedEvent(
        Long settlementId,
        String busNo,
        String typeEnum,
        Long cashierId,
        Long orgId,
        LocalDateTime settledAt,
        Integer paymentCount,
        Long operatorId) {
        super(settlementId, AggregateTypeConstants.CASHIER_SETTLEMENT);
        this.busNo = busNo;
        this.typeEnum = typeEnum;
        this.cashierId = cashierId;
        this.orgId = orgId;
        this.settledAt = settledAt;
        this.paymentCount = paymentCount;
        this.operatorId = operatorId;
    }
}
