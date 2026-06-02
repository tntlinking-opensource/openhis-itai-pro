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
 * 追溯轨迹已追加事件。
 */
@Getter
@Setter
public class TraceNumberRecordAppendedEvent extends DomainEvent {
    /**
     * 物品来源表名。
     */
    private String itemTable;
    /**
     * 物品主键。
     */
    private Long itemId;
    /**
     * 操作类型（{@link com.openhis.health.domain.share.enums.TraceNumberRecordOperationType#getCode()} 持久化串）。
     */
    private String operationType;
    /**
     * 业务单号（可空）。
     */
    private String busNo;
    /**
     * 本次追加追溯码数量。
     */
    private Integer traceCount;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TraceNumberRecordAppendedEvent() {
        super();
    }

    public TraceNumberRecordAppendedEvent(
        Long aggregateId,
        String itemTable,
        Long itemId,
        String operationType,
        String busNo,
        Integer traceCount
    ) {
        this(aggregateId, itemTable, itemId, operationType, busNo, traceCount, null);
    }

    public TraceNumberRecordAppendedEvent(
        Long aggregateId,
        String itemTable,
        Long itemId,
        String operationType,
        String busNo,
        Integer traceCount,
        Long operatorId
    ) {
        super(aggregateId, AggregateTypeConstants.TRACE_NUMBER_RECORD);
        this.itemTable = itemTable;
        this.itemId = itemId;
        this.operationType = operationType;
        this.busNo = busNo;
        this.traceCount = traceCount;
        this.operatorId = operatorId;
    }
}
