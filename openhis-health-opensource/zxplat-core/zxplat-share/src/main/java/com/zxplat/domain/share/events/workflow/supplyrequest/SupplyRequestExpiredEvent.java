/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.supplyrequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * SupplyRequestExpiredEvent 领域事件。
 */
@Getter
@Setter
public class SupplyRequestExpiredEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 单据类型。 */
    private String typeEnum;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplyRequestExpiredEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, String typeEnum, Long operatorId逻辑。
     * @return 返回结果
     */
    public SupplyRequestExpiredEvent(Long aggregateId, String busNo, String typeEnum, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_REQUEST);
        this.busNo = busNo;
        this.typeEnum = typeEnum;
        this.operatorId = operatorId;
    }
}
