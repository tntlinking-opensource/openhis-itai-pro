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

package com.openhis.health.domain.share.events.emergency.emgtriage;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * DM-EMG-001 §5.1：急诊分诊已更新；载荷宜带变更掩码或摘要。
 */
@Getter
@Setter
public class EmgTriageUpdatedEvent extends DomainEvent {

    private Long emgTriageId;
    /** 候诊相关字段变更位（项目自定义枚举或位标志字符串）。 */
    private Long updatedFieldMask;
    /** 若未计算 mask，则可填逗号分隔字段名摘要。 */
    private String updatedFieldsSummary;

    public EmgTriageUpdatedEvent() {
        super();
    }

    public EmgTriageUpdatedEvent(Long emgTriageId, Long updatedFieldMask, String updatedFieldsSummary) {
        super(emgTriageId, AggregateTypeConstants.EMERGENCY_TRIAGE);
        this.emgTriageId = emgTriageId;
        this.updatedFieldMask = updatedFieldMask;
        this.updatedFieldsSummary = updatedFieldsSummary;
    }
}
