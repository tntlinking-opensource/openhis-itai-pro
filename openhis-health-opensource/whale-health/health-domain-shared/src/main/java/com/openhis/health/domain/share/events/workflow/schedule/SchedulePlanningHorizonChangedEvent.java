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

package com.openhis.health.domain.share.events.workflow.schedule;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * SchedulePlanningHorizonChangedEvent 领域事件。
 */
@Getter
@Setter
public class SchedulePlanningHorizonChangedEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 旧窗口起。 */
    private java.time.LocalDateTime oldStart;

    /** 旧窗口止。 */
    private java.time.LocalDateTime oldEnd;

    /** 新窗口起。 */
    private java.time.LocalDateTime newStart;

    /** 新窗口止。 */
    private java.time.LocalDateTime newEnd;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SchedulePlanningHorizonChangedEvent() {
        super();
    }

    public SchedulePlanningHorizonChangedEvent(
        Long aggregateId,
        Long scheduleId,
        java.time.LocalDateTime oldStart,
        java.time.LocalDateTime oldEnd,
        java.time.LocalDateTime newStart,
        java.time.LocalDateTime newEnd,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SCHEDULE);
        this.scheduleId = scheduleId;
        this.oldStart = oldStart;
        this.oldEnd = oldEnd;
        this.newStart = newStart;
        this.newEnd = newEnd;
        this.operatorId = operatorId;
    }
}
