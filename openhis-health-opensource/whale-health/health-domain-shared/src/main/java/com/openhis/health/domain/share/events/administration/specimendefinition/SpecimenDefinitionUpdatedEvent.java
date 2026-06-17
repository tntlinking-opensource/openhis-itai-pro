/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.administration.specimendefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
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
