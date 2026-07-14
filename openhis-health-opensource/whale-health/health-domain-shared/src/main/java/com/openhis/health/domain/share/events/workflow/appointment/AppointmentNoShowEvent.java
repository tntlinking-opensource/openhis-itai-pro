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
 * 预约已过号事件。
 *
 * <p>SDD-WKF-002 §7 最小载荷：appointmentId（via super.aggregateId）、cancellationAt、reasonCode。</p>
 */
@Getter
@Setter
public class AppointmentNoShowEvent extends DomainEvent {

    /** 过号时间。 */
    private LocalDateTime cancellationAt;

    /** 过号原因代码。 */
    private String reasonCode;

    /**
     * 默认构造函数（反序列化用）。
     */
    public AppointmentNoShowEvent() {
        super();
    }

    /**
     * 构造预约已过号事件。
     *
     * @param appointmentId  候诊/预约项主键（aggregateId）
     * @param cancellationAt 过号时间
     * @param reasonCode     过号原因代码
     */
    public AppointmentNoShowEvent(Long appointmentId, LocalDateTime cancellationAt, String reasonCode) {
        super(appointmentId, AggregateTypeConstants.APPOINTMENT);
        this.cancellationAt = cancellationAt;
        this.reasonCode = reasonCode;
    }
}
