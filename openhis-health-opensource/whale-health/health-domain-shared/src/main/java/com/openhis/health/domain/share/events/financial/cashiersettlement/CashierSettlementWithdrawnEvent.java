/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.financial.cashiersettlement;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 交账撤回成功事件。
 */
@Getter
@Setter
public class CashierSettlementWithdrawnEvent extends DomainEvent {

    /** 操作人。 */
    private Long operatorId;

    /**
     * 无参构造（反序列化用）。
     */
    public CashierSettlementWithdrawnEvent() {
        super();
    }

    /**
     * 构造撤回事件。
     *
     * @param settlementId 交账头主键
     * @param operatorId   操作人
     */
    public CashierSettlementWithdrawnEvent(Long settlementId, Long operatorId) {
        super(settlementId, AggregateTypeConstants.CASHIER_SETTLEMENT);
        this.operatorId = operatorId;
    }
}
