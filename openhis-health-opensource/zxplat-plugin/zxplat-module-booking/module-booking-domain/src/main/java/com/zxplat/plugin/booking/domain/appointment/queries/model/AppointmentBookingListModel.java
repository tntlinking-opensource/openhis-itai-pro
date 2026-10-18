/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊预约列表读模型。
 */
@Getter
@Setter
public class AppointmentBookingListModel {

    /** 预约主键。 */
    private Long id;

    /** 业务单号。 */
    private String busNo;

    /** 预约分类。 */
    private String appointmentKind;

    /** 预约类型编码（医技场景存 serviceRequestId）。 */
    private String appointmentTypeCode;

    /** 患者主键。 */
    private Long patientId;

    /** 机构主键。 */
    private Long organizationId;

    /** 号源主键。 */
    private Long slotId;

    /** 日程主键（经 Slot 冗余解析，列表可为空）。 */
    private Long scheduleId;

    /** 计划开始。 */
    private LocalDateTime startInstant;

    /** 计划结束。 */
    private LocalDateTime endInstant;

    /** 去向诊室。 */
    private Long destinationLocationId;

    /** 展示状态（ACTIVE）。 */
    private String statusView;
}
