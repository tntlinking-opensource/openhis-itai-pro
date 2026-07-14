/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.queue.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 候诊队列列表行 DTO。
 */
@Getter
@Setter
public class QueueListItemDto {

    /** 候诊项主键。 */
    private Long appointmentId;

    /** 业务单号。 */
    private String busNo;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 就诊主键。 */
    private Long encounterId;

    /** 队列排序。 */
    private Integer displayOrder;

    /** 分诊等级。 */
    private String triageLevel;

    /** 预约分类。 */
    private String appointmentKind;

    /** 计划开始时刻。 */
    private LocalDateTime startInstant;

    /** 展示状态。 */
    private String statusView;

    /** 替诊展示文案（如「B替A」）。 */
    private String substituteDisplay;
}
