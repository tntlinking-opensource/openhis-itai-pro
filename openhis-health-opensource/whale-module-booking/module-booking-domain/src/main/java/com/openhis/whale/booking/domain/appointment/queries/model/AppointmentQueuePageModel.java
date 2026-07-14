/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 候诊队列分页列表行模型。
 */
@Getter
@Setter
public class AppointmentQueuePageModel {
    /** 主键。 */
    private Long id;
    /** 就诊主键。 */
    private Long encounterId;
    /** 分诊等级。 */
    private String triageLevel;

    /** 队列排序。 */
    private Integer displayOrder;

    /** 展示状态（ACTIVE）。 */
    private String statusView;

    /** 业务单号。 */
    private String busNo;

    /** 患者主键。 */
    private Long patientId;

    /** 预约分类。 */
    private String appointmentKind;

    /** 计划开始时刻。 */
    private java.time.LocalDateTime startInstant;

    /** 最近叫号时间。 */
    private java.time.LocalDateTime calledAt;

    /** 关联号源主键。 */
    private Long slotId;
}
