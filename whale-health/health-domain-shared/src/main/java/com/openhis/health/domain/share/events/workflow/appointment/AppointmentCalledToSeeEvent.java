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

package com.openhis.health.domain.share.events.workflow.appointment;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 预约已叫号看诊事件。
 *
 * <p>SDD-WKF-002 §7 最小载荷：appointmentId（via super.aggregateId）、calledAt、lastPractitionerId。</p>
 */
@Getter
@Setter
public class AppointmentCalledToSeeEvent extends DomainEvent {

    /** 叫号时间。 */
    private LocalDateTime calledAt;

    /** 叫号医生标识。 */
    private Long lastPractitionerId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public AppointmentCalledToSeeEvent() {
        super();
    }

    /**
     * 构造预约已叫号看诊事件。
     *
     * @param appointmentId       候诊/预约项主键（aggregateId）
     * @param calledAt            叫号时间
     * @param lastPractitionerId  叫号医生标识
     */
    public AppointmentCalledToSeeEvent(Long appointmentId, LocalDateTime calledAt, Long lastPractitionerId) {
        super(appointmentId, AggregateTypeConstants.APPOINTMENT);
        this.calledAt = calledAt;
        this.lastPractitionerId = lastPractitionerId;
    }
}
