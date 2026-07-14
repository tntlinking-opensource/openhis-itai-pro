/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.ordertemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 组套模板行已更新事件。
 */
@Getter
@Setter
public class OrderTemplateLineUpdatedEvent extends DomainEvent {
    /**
     * 组套模板行主键。
     */
    private Long lineId;

    /** 操作人主键（OrderTemplateLineUpdatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrderTemplateLineUpdatedEvent() {
        super();
    }

    /**
     * 处理Long orderTemplateId, Long lineId逻辑。
     * @return 返回结果
     */
    public OrderTemplateLineUpdatedEvent(Long orderTemplateId, Long lineId, Long operatorId) {
        super(orderTemplateId, AggregateTypeConstants.ORDER_TEMPLATE);
        this.lineId = lineId;
        this.operatorId = operatorId;
    }
}
