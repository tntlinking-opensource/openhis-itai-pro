/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.ordertemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;

/**
 * 组套模板已逻辑删除事件。
 */
public class OrderTemplateDeletedEvent extends DomainEvent {
    /** 操作人主键（OrderTemplateDeletedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrderTemplateDeletedEvent() {
        super();
    }

    /**
     * 处理Long orderTemplateId逻辑。
     * @return 返回结果
     */
    public OrderTemplateDeletedEvent(Long orderTemplateId, Long operatorId) {
        super(orderTemplateId, AggregateTypeConstants.ORDER_TEMPLATE);
        this.operatorId = operatorId;
    }
}
