/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 预约详情 DTO（4.6）。
 */
@Getter
@Setter
public class BookingDetailDto {

    /** 预约主键。 */
    private Long appointmentId;

    /** 业务单号。 */
    private String busNo;

    /** 预约分类。 */
    private String appointmentKind;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 机构主键。 */
    private Long organizationId;

    /** 号源主键。 */
    private Long slotId;

    /** 日程主键。 */
    private Long scheduleId;

    /** 医师主键（Schedule 参与者快照）。 */
    private Long practitionerId;

    /** 诊室主键。 */
    private Long destinationLocationId;

    /** 计划开始。 */
    private LocalDateTime startInstant;

    /** 计划结束。 */
    private LocalDateTime endInstant;

    /** 展示状态。 */
    private String statusView;
}
