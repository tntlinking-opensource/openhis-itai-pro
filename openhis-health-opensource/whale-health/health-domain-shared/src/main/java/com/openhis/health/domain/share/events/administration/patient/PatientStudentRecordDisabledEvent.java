/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.patient;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 学生资质停用事件。
 */
@Getter
@Setter
public class PatientStudentRecordDisabledEvent extends DomainEvent {

    /**
     * 学号。
     */
    private String studentNo;

    /**
     * 无参构造函数。
     */
    public PatientStudentRecordDisabledEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param studentNo 学号
     */
    public PatientStudentRecordDisabledEvent(Long patientId, String studentNo) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.studentNo = studentNo;
    }
}
