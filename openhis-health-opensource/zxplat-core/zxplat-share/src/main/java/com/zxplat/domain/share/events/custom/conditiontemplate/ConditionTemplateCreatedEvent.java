/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.conditiontemplate;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊断模板已创建事件。
 */
@Getter
@Setter
public class ConditionTemplateCreatedEvent extends DomainEvent {
    /** object主键（ConditionTemplateCreatedEvent）。 */
    private Long objectId;
    /** binding编码（ConditionTemplateCreatedEvent）。 */
    private String bindingEnum;
    /** definition主键（ConditionTemplateCreatedEvent）。 */
    private Long definitionId;

    /** 操作人主键（ConditionTemplateCreatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ConditionTemplateCreatedEvent() {
        super();
    }

    /**
     * 处理Long templateId, Long objectId, String bindingEnum, Long definitionId逻辑。
     * @return 返回结果
     */
    public ConditionTemplateCreatedEvent(Long templateId, Long objectId, String bindingEnum, Long definitionId, Long operatorId) {
        super(templateId, AggregateTypeConstants.CONDITION_TEMPLATE);
        this.objectId = objectId;
        this.bindingEnum = bindingEnum;
        this.definitionId = definitionId;
        this.operatorId = operatorId;
    }
}
