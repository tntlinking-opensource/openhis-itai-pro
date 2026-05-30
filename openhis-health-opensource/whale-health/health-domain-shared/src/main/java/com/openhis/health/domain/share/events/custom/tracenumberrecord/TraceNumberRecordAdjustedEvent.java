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

package com.openhis.health.domain.share.events.custom.tracenumberrecord;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 追溯轨迹差异调整事件。
 */
@Getter
@Setter
public class TraceNumberRecordAdjustedEvent extends DomainEvent {
    /**
     * 本次追加条数。
     */
    private Integer appendedCount;
    /**
     * 本次移除条数。
     */
    private Integer removedCount;
    /**
     * 业务单号（可空）。
     */
    private String busNo;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TraceNumberRecordAdjustedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Integer appendedCount, Integer removedCount, String busNo逻辑。
     * @return 返回结果
     */
    public TraceNumberRecordAdjustedEvent(Long aggregateId, Integer appendedCount, Integer removedCount, String busNo, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.TRACE_NUMBER_RECORD);
        this.appendedCount = appendedCount;
        this.removedCount = removedCount;
        this.busNo = busNo;
        this.operatorId = operatorId;
    }
}
