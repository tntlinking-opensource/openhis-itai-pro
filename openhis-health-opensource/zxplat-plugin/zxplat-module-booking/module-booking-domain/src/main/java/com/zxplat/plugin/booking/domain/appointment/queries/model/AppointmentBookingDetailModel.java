/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊预约详情读模型。
 */
@Getter
@Setter
public class AppointmentBookingDetailModel {

    /** 预约主键。 */
    private Long id;

    /** 业务单号。 */
    private String busNo;

    /** 预约分类。 */
    private String appointmentKind;

    /** 患者主键。 */
    private Long patientId;

    /** 机构主键。 */
    private Long organizationId;

    /** 号源主键。 */
    private Long slotId;

    /** 就诊主键（已转挂号时不为空）。 */
    private Long encounterId;

    /** 医师主键（来自日程参与者）。 */
    private Long practitionerId;

    /** 号源类型/卫生服务主键（来自日程）。 */
    private Long healthcareServiceId;

    /** 日程主键。 */
    private Long scheduleId;

    /** 计划开始。 */
    private LocalDateTime startInstant;

    /** 计划结束。 */
    private LocalDateTime endInstant;

    /** 去向诊室。 */
    private Long destinationLocationId;

    /** 预约类型编码（医技场景存 serviceRequestId）。 */
    private String appointmentTypeCode;

    /** 队列显示顺序。 */
    private Integer displayOrder;

    /** 展示状态。 */
    private String statusView;

    /** 取消原因。 */
    private String cancelReason;

    /** 取消时间。 */
    private LocalDateTime cancellationAt;

    /** 最近叫号时间。 */
    private LocalDateTime calledAt;

    /** 渠道来源编码。 */
    private String originChannel;
}
