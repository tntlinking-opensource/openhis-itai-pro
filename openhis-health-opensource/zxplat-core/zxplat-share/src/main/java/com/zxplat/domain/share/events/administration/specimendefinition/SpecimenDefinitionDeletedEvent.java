/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.specimendefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 样本定义已删除事件（DM §5.1）；逻辑删除成功后发布，载荷含主键。
 */
@Getter
@Setter
public class SpecimenDefinitionDeletedEvent extends DomainEvent {

    /**
     * @param specimenDefinitionId 样本定义主键
     */
    public SpecimenDefinitionDeletedEvent(Long specimenDefinitionId) {
        super(specimenDefinitionId, AggregateTypeConstants.SPECIMEN_DEFINITION);
    }
}
