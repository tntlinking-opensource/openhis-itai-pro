/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.ordertemplate;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 组套模板已更新事件。
 */
@Getter
@Setter
public class OrderTemplateUpdatedEvent extends DomainEvent {
    /**
     * 变更字段键列表（列名或统一约定的属性键，由写侧计算）。
     */
    private List<String> changedKeys;

    /** 操作人主键（OrderTemplateUpdatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrderTemplateUpdatedEvent() {
        super();
    }

    /**
     * 处理Long orderTemplateId, List<String> changedKeys逻辑。
     * @return 返回结果
     */
    public OrderTemplateUpdatedEvent(Long orderTemplateId, List<String> changedKeys, Long operatorId) {
        super(orderTemplateId, AggregateTypeConstants.ORDER_TEMPLATE);
        this.changedKeys = changedKeys;
        this.operatorId = operatorId;
    }
}
