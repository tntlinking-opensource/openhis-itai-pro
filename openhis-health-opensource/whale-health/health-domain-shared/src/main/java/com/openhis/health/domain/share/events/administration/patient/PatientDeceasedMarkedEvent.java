/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
