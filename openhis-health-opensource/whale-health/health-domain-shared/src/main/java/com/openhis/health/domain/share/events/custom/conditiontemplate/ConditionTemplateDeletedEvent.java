/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.conditiontemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊断模板已删除事件。
 */
@Getter
@Setter
public class ConditionTemplateDeletedEvent extends DomainEvent {
    /** definition主键（ConditionTemplateDeletedEvent）。 */
    private Long definitionId;

    /** 操作人主键（ConditionTemplateDeletedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ConditionTemplateDeletedEvent() {
        super();
    }

    /**
     * 处理Long templateId, Long definitionId逻辑。
     * @return 返回结果
     */
    public ConditionTemplateDeletedEvent(Long templateId, Long definitionId, Long operatorId) {
        super(templateId, AggregateTypeConstants.CONDITION_TEMPLATE);
        this.definitionId = definitionId;
        this.operatorId = operatorId;
    }
}
