/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.specimendefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 样本定义已启用事件（DM §5.1）；批量启用成功后逐条发布，载荷含主键与目标状态。
 */
@Getter
@Setter
public class SpecimenDefinitionActivatedEvent extends DomainEvent {
    /** 发布状态。 */
    private String statusEnum;

    /**
     * @param specimenDefinitionId 样本定义主键
     * @param statusEnum 发布状态
     */
    public SpecimenDefinitionActivatedEvent(Long specimenDefinitionId, String statusEnum) {
        super(specimenDefinitionId, AggregateTypeConstants.SPECIMEN_DEFINITION);
        this.statusEnum = statusEnum;
    }
}
