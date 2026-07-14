/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 患者侧预约/候诊列表模型。
 */
@Getter
@Setter
public class PatientAppointmentModel {
    /** 主键。 */
    private Long id;
    /** 患者主键。 */
    private Long patientId;
    /** 号源主键。 */
    private Long slotId;
    /** 开始instant。 */
    private LocalDateTime startInstant;
}
