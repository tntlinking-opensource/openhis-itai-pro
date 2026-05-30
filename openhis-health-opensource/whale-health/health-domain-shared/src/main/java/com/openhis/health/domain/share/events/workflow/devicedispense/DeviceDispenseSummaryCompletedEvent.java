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

package com.openhis.health.domain.share.events.workflow.devicedispense;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * DeviceDispenseSummaryCompletedEvent 领域事件。
 */
@Getter
@Setter
public class DeviceDispenseSummaryCompletedEvent extends DomainEvent {

    /** 汇总锚点（以落库与交互契约为准，可能为业务号或批次号）。 */
    private String batchKey;

    /** 发放标识列表。 */
    private java.util.List<Long> idList;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public DeviceDispenseSummaryCompletedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, String batchKey, java.util.List<Long> idList, Long operatorId逻辑。
     * @return 返回结果
     */
    public DeviceDispenseSummaryCompletedEvent(Long aggregateId, String batchKey, java.util.List<Long> idList, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.DEVICE_DISPENSE);
        this.batchKey = batchKey;
        this.idList = idList;
        this.operatorId = operatorId;
    }
}
