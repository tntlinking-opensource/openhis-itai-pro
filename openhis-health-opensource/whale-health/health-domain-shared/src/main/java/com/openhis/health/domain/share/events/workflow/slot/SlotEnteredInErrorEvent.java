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
 * SlotEnteredInErrorEvent 领域事件。
 */
@Getter
@Setter
public class SlotEnteredInErrorEvent extends DomainEvent {

    /** 号源时段标识。 */
    private Long slotId;

    /** 原因（可选）。 */
    private String reason;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SlotEnteredInErrorEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long slotId, String reason逻辑。
     * @return 返回结果
     */
    public SlotEnteredInErrorEvent(Long aggregateId, Long slotId, String reason, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SLOT);
        this.slotId = slotId;
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
