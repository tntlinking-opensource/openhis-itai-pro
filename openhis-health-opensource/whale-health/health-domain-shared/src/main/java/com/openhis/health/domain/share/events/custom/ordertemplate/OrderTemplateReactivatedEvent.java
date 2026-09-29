/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.ordertemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;

/**
 * 组套模板已重新启用（RETIRED → ACTIVE）。
 */
public class OrderTemplateReactivatedEvent extends DomainEvent {
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 反序列化用空构造。
     */
    public OrderTemplateReactivatedEvent() {
        super();
    }

    /**
     * 记录被重新启用的组套与操作人。
     *
     * @param orderTemplateId 组套头主键
     * @param operatorId 操作人主键
     */
    public OrderTemplateReactivatedEvent(Long orderTemplateId, Long operatorId) {
        super(orderTemplateId, AggregateTypeConstants.ORDER_TEMPLATE);
        this.operatorId = operatorId;
    }
}
