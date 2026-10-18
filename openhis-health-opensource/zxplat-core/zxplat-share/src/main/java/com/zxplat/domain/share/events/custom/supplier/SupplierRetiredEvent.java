/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.supplier;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应商已废弃事件。
 */
@Getter
@Setter
public class SupplierRetiredEvent extends DomainEvent {
    /**
     * 供应商业务编码。
     */
    private String busNo;
    /**
     * 废弃原因（可空）。
     */
    private String reason;

    /** 操作人主键（SupplierRetiredEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplierRetiredEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, String reason逻辑。
     * @return 返回结果
     */
    public SupplierRetiredEvent(Long aggregateId, String busNo, String reason, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLIER);
        this.busNo = busNo;
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
