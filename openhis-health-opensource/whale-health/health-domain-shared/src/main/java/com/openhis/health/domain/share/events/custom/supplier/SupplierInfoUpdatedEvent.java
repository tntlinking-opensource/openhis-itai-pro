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
 * 供应商信息已更新事件。
 */
@Getter
@Setter
public class SupplierInfoUpdatedEvent extends DomainEvent {
    /**
     * 变更字段摘要（逗号分隔或 JSON，口径以写侧实现为准）。
     */
    private String changedFields;

    /** 操作人主键（SupplierInfoUpdatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplierInfoUpdatedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String changedFields逻辑。
     * @return 返回结果
     */
    public SupplierInfoUpdatedEvent(Long aggregateId, String changedFields, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLIER);
        this.changedFields = changedFields;
        this.operatorId = operatorId;
    }
}
