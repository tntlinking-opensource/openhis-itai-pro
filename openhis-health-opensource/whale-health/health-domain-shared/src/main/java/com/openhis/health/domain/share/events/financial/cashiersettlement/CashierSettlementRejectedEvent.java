/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.financial.cashiersettlement;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 交账驳回成功事件。
 */
@Getter
@Setter
public class CashierSettlementRejectedEvent extends DomainEvent {

    /** 驳回人。 */
    private Long approverId;

    /** 驳回时刻（落库 approved_at）。 */
    private LocalDateTime approvedAt;

    /** 驳回原因。 */
    private String reason;

    /**
     * 无参构造（反序列化用）。
     */
    public CashierSettlementRejectedEvent() {
        super();
    }

    /**
     * 构造驳回事件。
     *
     * @param settlementId 交账头主键
     * @param approverId   驳回人
     * @param approvedAt   驳回时刻
     * @param reason       原因
     */
    public CashierSettlementRejectedEvent(
        Long settlementId, Long approverId, LocalDateTime approvedAt, String reason) {
        super(settlementId, AggregateTypeConstants.CASHIER_SETTLEMENT);
        this.approverId = approverId;
        this.approvedAt = approvedAt;
        this.reason = reason;
    }
}
