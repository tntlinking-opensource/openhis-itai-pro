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
 * 患者逻辑删除事件。
 */
@Getter
@Setter
public class PatientDeletedEvent extends DomainEvent {

    /**
     * 院内编码。
     */
    private String busNo;

    /**
     * 删除时间。
     */
    private LocalDateTime deletedAt;

    /**
     * 无参构造函数。
     */
    public PatientDeletedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param busNo 院内编码
     * @param deletedAt 删除时间
     */
    public PatientDeletedEvent(Long patientId, String busNo, LocalDateTime deletedAt) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.busNo = busNo;
        this.deletedAt = deletedAt;
    }
}
