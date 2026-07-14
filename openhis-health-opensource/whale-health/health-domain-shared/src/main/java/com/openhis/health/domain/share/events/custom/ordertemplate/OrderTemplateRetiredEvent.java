/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.ordertemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;

/**
 * 组套模板已停用事件。
 */
public class OrderTemplateRetiredEvent extends DomainEvent {
    /** 操作人主键（OrderTemplateRetiredEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrderTemplateRetiredEvent() {
        super();
    }

    /**
     * 处理Long orderTemplateId逻辑。
     * @return 返回结果
     */
    public OrderTemplateRetiredEvent(Long orderTemplateId, Long operatorId) {
        super(orderTemplateId, AggregateTypeConstants.ORDER_TEMPLATE);
        this.operatorId = operatorId;
    }
}
