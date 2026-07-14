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
 * 组套模板行已新增事件。
 */
@Getter
@Setter
public class OrderTemplateLineAddedEvent extends DomainEvent {
    /**
     * 新增组套模板行主键。
     */
    private Long lineId;
    /**
     * 行类型枚举值（药品/诊疗/耗材）。
     */
    private String lineKindEnum;

    /** 操作人主键（OrderTemplateLineAddedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrderTemplateLineAddedEvent() {
        super();
    }

    /**
     * 处理Long orderTemplateId, Long lineId, Integer lineKindEnum逻辑。
     * @return 返回结果
     */
    public OrderTemplateLineAddedEvent(Long orderTemplateId, Long lineId, String lineKindEnum, Long operatorId) {
        super(orderTemplateId, AggregateTypeConstants.ORDER_TEMPLATE);
        this.lineId = lineId;
        this.lineKindEnum = lineKindEnum;
        this.operatorId = operatorId;
    }
}
