/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.result;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 门诊预约快照，供挂号台 IF-REG-12 预填。 */
@Getter
@Setter
public class OutpatientBookingSnapshot {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务编号。 */
    private String appointmentBusNo;

    /** 号源主键。 */
    private Long slotId;

    /** 科室/机构主键。 */
    private Long organizationId;

    /** 医师主键。 */
    private Long practitionerId;

    /** 号源类型（HealthcareService）主键。 */
    private Long healthcareServiceId;

    /** 号源开始时刻。 */
    private LocalDateTime slotStart;

    /** 号源结束时刻。 */
    private LocalDateTime slotEnd;

    /** 科室名称（展示用）。 */
    private String organizationName;

    /** 医师姓名（展示用）。 */
    private String practitionerName;

    /** 号源类型名称（展示用）。 */
    private String serviceTypeName;

    /** 预约状态枚举 code。 */
    private String statusEnum;

    /** 关联就诊主键（已挂号时）。 */
    private Long encounterId;
}
