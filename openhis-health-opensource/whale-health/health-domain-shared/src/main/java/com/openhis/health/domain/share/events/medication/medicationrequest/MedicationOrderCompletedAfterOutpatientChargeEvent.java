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

import java.time.LocalDateTime;

/**
 * 门诊收费完成后药品医嘱置「已完成」事件。
 *
 * <p>与住院核对产生的 {@link MedicationOrderVerifiedEvent} 区分：门诊收费链路直接将已发送医嘱推进为 COMPLETED。</p>
 */
@Getter
@Setter
public class MedicationOrderCompletedAfterOutpatientChargeEvent extends DomainEvent {

    private Long operatorId;

    private LocalDateTime completedTime;

    public MedicationOrderCompletedAfterOutpatientChargeEvent() {
        super();
    }

    public MedicationOrderCompletedAfterOutpatientChargeEvent(Long medicationRequestId, Long operatorId, LocalDateTime completedTime) {
        super(medicationRequestId, AggregateTypeConstants.MEDICATION_REQUEST);
        this.operatorId = operatorId;
        this.completedTime = completedTime;
    }
}
