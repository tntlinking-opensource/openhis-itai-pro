/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.conditiondefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊断定义已停用事件。
 */
@Getter
@Setter
public class ConditionDefinitionDisabledEvent extends DomainEvent {
    /** 状态编码（ConditionDefinitionDisabledEvent）。 */
    private String statusEnum;

    /**
     * 构造事件。
     */
    public ConditionDefinitionDisabledEvent(Long id, String statusEnum) {
        super(id, AggregateTypeConstants.CONDITION_DEFINITION);
        this.statusEnum = statusEnum;
    }
}
