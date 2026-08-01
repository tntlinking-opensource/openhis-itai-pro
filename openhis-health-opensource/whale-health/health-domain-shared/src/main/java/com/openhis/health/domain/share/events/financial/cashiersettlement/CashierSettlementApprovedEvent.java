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
 * 交账审批通过事件。
 */
@Getter
@Setter
public class CashierSettlementApprovedEvent extends DomainEvent {

    /** 审批人。 */
    private Long approverId;

    /** 审批时间。 */
    private LocalDateTime approvedAt;

    /**
     * 无参构造（反序列化用）。
     */
    public CashierSettlementApprovedEvent() {
        super();
    }

    /**
     * 构造审批通过事件。
     *
     * @param settlementId 交账头主键
     * @param approverId   审批人
     * @param approvedAt   审批时间
     */
    public CashierSettlementApprovedEvent(Long settlementId, Long approverId, LocalDateTime approvedAt) {
        super(settlementId, AggregateTypeConstants.CASHIER_SETTLEMENT);
        this.approverId = approverId;
        this.approvedAt = approvedAt;
    }
}
