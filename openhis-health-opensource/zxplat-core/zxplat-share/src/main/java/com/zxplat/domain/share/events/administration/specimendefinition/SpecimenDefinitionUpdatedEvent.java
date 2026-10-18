/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.specimendefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 样本定义已更新事件（DM §5.1）；维护目录字段成功后发布，载荷含变更字段摘要。
 */
@Getter
@Setter
public class SpecimenDefinitionUpdatedEvent extends DomainEvent {
    /** 变更字段摘要。 */
    private List<String> changedFields;

    /**
     * @param specimenDefinitionId 样本定义主键
     * @param changedFields 变更字段名列表
     */
    public SpecimenDefinitionUpdatedEvent(Long specimenDefinitionId, List<String> changedFields) {
        super(specimenDefinitionId, AggregateTypeConstants.SPECIMEN_DEFINITION);
        this.changedFields = changedFields;
    }
}
