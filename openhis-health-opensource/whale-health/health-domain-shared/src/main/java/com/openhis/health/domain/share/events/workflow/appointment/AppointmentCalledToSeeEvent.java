/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
