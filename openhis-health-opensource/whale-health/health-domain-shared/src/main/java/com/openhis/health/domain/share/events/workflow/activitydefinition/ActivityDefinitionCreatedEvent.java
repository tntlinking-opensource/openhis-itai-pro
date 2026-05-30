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

package com.openhis.health.domain.share.events.workflow.activitydefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗项目定义已创建事件。
 *
 * <p>SDD-WKF-001 §7 最小载荷：activityDefinitionId（via super.aggregateId）、busNo、name。</p>
 */
@Getter
@Setter
public class ActivityDefinitionCreatedEvent extends DomainEvent {

    /** 业务编码。 */
    private String busNo;

    /** 诊疗项目名称。 */
    private String name;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ActivityDefinitionCreatedEvent() {
        super();
    }

    /**
     * 构造诊疗项目定义已创建事件。
     *
     * @param activityDefinitionId 诊疗项目主键（aggregateId）
     * @param busNo                业务编码
     * @param name                 诊疗项目名称
     */
    public ActivityDefinitionCreatedEvent(Long activityDefinitionId, String busNo, String name) {
        super(activityDefinitionId, AggregateTypeConstants.ACTIVITY_DEFINITION);
        this.busNo = busNo;
        this.name = name;
    }
}
