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
 * 样本定义已创建事件（DM §5.1）；新建目录成功后发布，载荷含主键与关键快照。
 */
@Getter
@Setter
public class SpecimenDefinitionCreatedEvent extends DomainEvent {
    /** 业务编码。 */
    private String busNo;
    /** 样本名称。 */
    private String name;
    /** 标本种类。 */
    private String typeEnum;
    /** 发布状态。 */
    private String statusEnum;

    /**
     * @param specimenDefinitionId 样本定义主键
     * @param busNo 业务编码
     * @param name 样本名称
     * @param typeEnum 标本种类
     * @param statusEnum 发布状态
     */
    public SpecimenDefinitionCreatedEvent(Long specimenDefinitionId, String busNo, String name, String typeEnum, String statusEnum) {
        super(specimenDefinitionId, AggregateTypeConstants.SPECIMEN_DEFINITION);
        this.busNo = busNo;
        this.name = name;
        this.typeEnum = typeEnum;
        this.statusEnum = statusEnum;
    }
}
