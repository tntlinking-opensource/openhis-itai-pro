/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.supplier;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应商已恢复启用事件。
 */
@Getter
@Setter
public class SupplierReactivatedEvent extends DomainEvent {
    /**
     * 供应商业务编码。
     */
    private String busNo;

    /** 操作人主键（SupplierReactivatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplierReactivatedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo逻辑。
     * @return 返回结果
     */
    public SupplierReactivatedEvent(Long aggregateId, String busNo, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLIER);
        this.busNo = busNo;
        this.operatorId = operatorId;
    }
}
