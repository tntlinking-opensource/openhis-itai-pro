/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.conditiondefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 诊断定义已编辑事件。
 */
@Getter
@Setter
public class ConditionDefinitionEditedEvent extends DomainEvent {
    /** changedFields（ConditionDefinitionEditedEvent）。 */
    private List<String> changedFields;

    /**
     * 构造事件。
     */
    public ConditionDefinitionEditedEvent(Long id, List<String> changedFields) {
        super(id, AggregateTypeConstants.CONDITION_DEFINITION);
        this.changedFields = changedFields;
    }
}
