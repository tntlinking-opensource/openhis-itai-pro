/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.ordertemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.enums.OrderTemplateScene;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 组套模板已创建事件。
 */
@Getter
@Setter
public class OrderTemplateCreatedEvent extends DomainEvent {
    /**
     * 归属对象标识（语义由 bindingEnum 决定）。
     */
    private Long objectId;
    /**
     * 绑定类型枚举值（个人/科室/全院）。
     */
    private String bindingEnum;
    /**
     * 适用场景枚举值（门诊/住院/通用）。
     */
    private OrderTemplateScene sceneEnum;

    /** 操作人主键（OrderTemplateCreatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrderTemplateCreatedEvent() {
        super();
    }

    /**
     * 处理Long orderTemplateId, Long objectId, String bindingEnum, Integer sceneEnum逻辑。
     * @return 返回结果
     */
    public OrderTemplateCreatedEvent(Long orderTemplateId, Long objectId, String bindingEnum, OrderTemplateScene sceneEnum, Long operatorId) {
        super(orderTemplateId, AggregateTypeConstants.ORDER_TEMPLATE);
        this.objectId = objectId;
        this.bindingEnum = bindingEnum;
        this.sceneEnum = sceneEnum;
        this.operatorId = operatorId;
    }
}
