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
 * 供应商已停用事件。
 */
@Getter
@Setter
public class SupplierSuspendedEvent extends DomainEvent {
    /**
     * 供应商业务编码。
     */
    private String busNo;
    /**
     * 停用原因（可空）。
     */
    private String reason;

    /** 操作人主键（SupplierSuspendedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplierSuspendedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, String reason逻辑。
     * @return 返回结果
     */
    public SupplierSuspendedEvent(Long aggregateId, String busNo, String reason, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLIER);
        this.busNo = busNo;
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
