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

package com.openhis.health.domain.share.events.medication.medicationrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品医嘱取消事件。
 */
@Getter
@Setter
public class MedicationOrderCancelledEvent extends DomainEvent {
    /** 取消原因。 */
    private String cancelReason;

    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationOrderCancelledEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationOrderCancelledEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, String cancelReason逻辑。
     * @return 返回结果
     */
    public MedicationOrderCancelledEvent(Long id, String cancelReason) {
        this(id, cancelReason, null);
    }

    public MedicationOrderCancelledEvent(Long id, String cancelReason, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.cancelReason = cancelReason;
        this.operatorId = operatorId;
    }
}
