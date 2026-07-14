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
 * 学生资质新增或更新事件。
 */
@Getter
@Setter
public class PatientStudentRecordUpsertedEvent extends DomainEvent {

    /**
     * 学生记录标识。
     */
    private Long studentId;

    /**
     * 学号。
     */
    private String studentNo;

    /**
     * 在校状态。
     */
    private Integer schoolStatus;

    /**
     * 无参构造函数。
     */
    public PatientStudentRecordUpsertedEvent() {
        super();
    }

    /**
     * 事件构造函数。
     *
     * @param patientId 患者标识
     * @param studentId 学生记录标识
     * @param studentNo 学号
     * @param schoolStatus 在校状态
     */
    public PatientStudentRecordUpsertedEvent(Long patientId, Long studentId, String studentNo, Integer schoolStatus) {
        super(patientId, AggregateTypeConstants.PATIENT);
        this.studentId = studentId;
        this.studentNo = studentNo;
        this.schoolStatus = schoolStatus;
    }
}
