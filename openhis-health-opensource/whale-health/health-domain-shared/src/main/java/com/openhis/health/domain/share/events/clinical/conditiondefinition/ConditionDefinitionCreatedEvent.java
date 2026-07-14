/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.clinical.conditiondefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊断定义已创建事件。
 */
@Getter
@Setter
public class ConditionDefinitionCreatedEvent extends DomainEvent {
    /** condition编码（ConditionDefinitionCreatedEvent）。 */
    private String conditionCode;
    /** name（ConditionDefinitionCreatedEvent）。 */
    private String name;
    /** 状态编码（ConditionDefinitionCreatedEvent）。 */
    private String statusEnum;

    /**
     * 构造事件。
     */
    public ConditionDefinitionCreatedEvent(Long id, String conditionCode, String name, String statusEnum) {
        super(id, AggregateTypeConstants.CONDITION_DEFINITION);
        this.conditionCode = conditionCode;
        this.name = name;
        this.statusEnum = statusEnum;
    }
}
