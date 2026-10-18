/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.ordertemplate;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;

/**
 * 组套模板已发布事件。
 */
public class OrderTemplatePublishedEvent extends DomainEvent {
    /** 操作人主键（OrderTemplatePublishedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrderTemplatePublishedEvent() {
        super();
    }

    /**
     * 处理Long orderTemplateId逻辑。
     * @return 返回结果
     */
    public OrderTemplatePublishedEvent(Long orderTemplateId, Long operatorId) {
        super(orderTemplateId, AggregateTypeConstants.ORDER_TEMPLATE);
        this.operatorId = operatorId;
    }
}
