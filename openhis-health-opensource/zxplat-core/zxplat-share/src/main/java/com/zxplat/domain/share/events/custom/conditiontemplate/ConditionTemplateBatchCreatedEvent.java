/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.conditiontemplate;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 诊断模板批量已创建事件。
 */
@Getter
@Setter
public class ConditionTemplateBatchCreatedEvent extends DomainEvent {
    /** object主键（ConditionTemplateBatchCreatedEvent）。 */
    private Long objectId;
    /** binding编码（ConditionTemplateBatchCreatedEvent）。 */
    private String bindingEnum;
    /** templateIds（ConditionTemplateBatchCreatedEvent）。 */
    private List<Long> templateIds;

    /** 操作人主键（ConditionTemplateBatchCreatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ConditionTemplateBatchCreatedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long objectId, String bindingEnum, List<Long> templateIds逻辑。
     * @return 返回结果
     */
    public ConditionTemplateBatchCreatedEvent(Long aggregateId, Long objectId, String bindingEnum, List<Long> templateIds, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.CONDITION_TEMPLATE);
        this.objectId = objectId;
        this.bindingEnum = bindingEnum;
        this.templateIds = templateIds;
        this.operatorId = operatorId;
    }
}
