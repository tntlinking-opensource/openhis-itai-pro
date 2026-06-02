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

package com.openhis.health.domain.share.events.workflow.slot;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * SlotBatchProvisionedEvent 领域事件。
 */
@Getter
@Setter
public class SlotBatchProvisionedEvent extends DomainEvent {

    /** 号源日程标识。 */
    private Long scheduleId;

    /** 生成条数。 */
    private Integer generatedCount;

    /** 新建时段标识列表（可用于下游预热/索引）。 */
    private java.util.List<Long> slotIdList;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotBatchProvisionedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long scheduleId, Integer generatedCount, java.util.List<Long> slotIdList逻辑。
     * @return 返回结果
     */
    public SlotBatchProvisionedEvent(Long aggregateId, Long scheduleId, Integer generatedCount, java.util.List<Long> slotIdList, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.scheduleId = scheduleId;
        this.generatedCount = generatedCount;
        this.slotIdList = slotIdList;
        this.operatorId = operatorId;
    }
}
