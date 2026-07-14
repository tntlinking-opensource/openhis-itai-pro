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
 * 供应商已注册事件。
 */
@Getter
@Setter
public class SupplierRegisteredEvent extends DomainEvent {
    /**
     * 供应商业务编码。
     */
    private String busNo;
    /**
     * 供应商名称。
     */
    private String name;
    /**
     * 供应商类型枚举值。
     */
    private String typeEnum;

    /** 操作人主键（SupplierRegisteredEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplierRegisteredEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String busNo, String name, Integer typeEnum逻辑。
     * @return 返回结果
     */
    public SupplierRegisteredEvent(Long aggregateId, String busNo, String name, String typeEnum, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLIER);
        this.busNo = busNo;
        this.name = name;
        this.typeEnum = typeEnum;
        this.operatorId = operatorId;
    }
}
