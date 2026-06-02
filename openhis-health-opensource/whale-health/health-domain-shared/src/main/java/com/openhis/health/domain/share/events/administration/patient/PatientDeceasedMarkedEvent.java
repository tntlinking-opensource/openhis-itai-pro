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

package com.openhis.health.domain.share.events.administration.patient;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 患者死亡标记事件。
 */
@Getter
@Setter
public class PatientDeceasedMarkedEvent extends DomainEvent {

    /**
     * 死亡时间。
     */
    private LocalDateTime deceasedDate;

    /**
     * 死亡原因。
     */
    private String deceasedReason;

    /**
     * 无参构造函数。
     */
    public PatientDeceasedMarkedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param deceasedDate 死亡时间
     * @param deceasedReason 死亡原因
     */
    public PatientDeceasedMarkedEvent(Long patientId, LocalDateTime deceasedDate, String deceasedReason) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.deceasedDate = deceasedDate;
        this.deceasedReason = deceasedReason;
    }
}
