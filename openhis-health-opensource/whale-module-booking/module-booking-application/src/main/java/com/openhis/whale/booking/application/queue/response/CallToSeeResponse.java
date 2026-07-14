/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.queue.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 叫号看诊响应（5.2）。
 */
@Getter
@Setter
public class CallToSeeResponse {

    /** 候诊项主键。 */
    private Long appointmentId;

    /** 业务单号。 */
    private String busNo;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 队列排序。 */
    private Integer displayOrder;

    /** 叫号时间。 */
    private LocalDateTime calledAt;

    /** 叫号医师主键。 */
    private Long practitionerId;
}
