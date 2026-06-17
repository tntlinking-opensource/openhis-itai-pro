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
