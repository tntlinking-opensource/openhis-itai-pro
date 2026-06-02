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

package com.openhis.health.domain.share.events.custom.servicelocationtemplate;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 取药科室模板已编辑事件。
 */
@Getter
@Setter
public class ServiceLocationTemplateEditedEvent extends DomainEvent {
    private Long organizationId;
    private Integer ruleCount;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ServiceLocationTemplateEditedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long organizationId, Integer ruleCount逻辑。
     * @return 返回结果
     */
    public ServiceLocationTemplateEditedEvent(Long aggregateId, Long organizationId, Integer ruleCount, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SERVICE_LOCATION_TEMPLATE);
        this.organizationId = organizationId;
        this.ruleCount = ruleCount;
        this.operatorId = operatorId;
    }
}
