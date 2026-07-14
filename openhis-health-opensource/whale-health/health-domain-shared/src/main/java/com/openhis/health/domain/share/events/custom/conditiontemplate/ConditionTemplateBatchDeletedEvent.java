/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.conditiontemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 诊断模板批量已删除事件。
 */
@Getter
@Setter
public class ConditionTemplateBatchDeletedEvent extends DomainEvent {
    /** templateIds（ConditionTemplateBatchDeletedEvent）。 */
    private List<Long> templateIds;

    /** 操作人主键（ConditionTemplateBatchDeletedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ConditionTemplateBatchDeletedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, List<Long> templateIds逻辑。
     * @return 返回结果
     */
    public ConditionTemplateBatchDeletedEvent(Long aggregateId, List<Long> templateIds, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.CONDITION_TEMPLATE);
        this.templateIds = templateIds;
        this.operatorId = operatorId;
    }
}
