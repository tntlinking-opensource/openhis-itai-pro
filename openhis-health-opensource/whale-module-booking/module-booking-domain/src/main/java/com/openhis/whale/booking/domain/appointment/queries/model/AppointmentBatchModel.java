/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 批量按就诊查询的回填模型。
 */
@Getter
@Setter
public class AppointmentBatchModel {
    /** 就诊主键。 */
    private Long encounterId;
    /** 预约主键。 */
    private Long appointmentId;
    /** 队列状态。 */
    private String queueStatus;
}
