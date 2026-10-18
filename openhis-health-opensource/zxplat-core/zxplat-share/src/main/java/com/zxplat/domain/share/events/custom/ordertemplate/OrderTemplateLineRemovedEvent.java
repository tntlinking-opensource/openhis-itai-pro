/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.ordertemplate;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 组套模板行已移除事件。
 */
@Getter
@Setter
public class OrderTemplateLineRemovedEvent extends DomainEvent {
    /**
     * 被移除的组套模板行主键。
     */
    private Long lineId;

    /** 操作人主键（OrderTemplateLineRemovedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrderTemplateLineRemovedEvent() {
        super();
    }

    /**
     * 处理Long orderTemplateId, Long lineId逻辑。
     * @return 返回结果
     */
    public OrderTemplateLineRemovedEvent(Long orderTemplateId, Long lineId, Long operatorId) {
        super(orderTemplateId, AggregateTypeConstants.ORDER_TEMPLATE);
        this.lineId = lineId;
        this.operatorId = operatorId;
    }
}
