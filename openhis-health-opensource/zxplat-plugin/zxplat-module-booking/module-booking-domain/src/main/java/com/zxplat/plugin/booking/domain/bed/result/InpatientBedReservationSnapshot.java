/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.bed.result;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 住院床位预约快照（health 分床联动读 Reader）。
 */
@Getter
@Setter
public class InpatientBedReservationSnapshot {

    /** 预约主键。 */
    private Long appointmentId;

    /** 床位 Location 主键。 */
    private Long bedLocationId;

    /** 患者主键。 */
    private Long patientId;

    /** 拟入院日期。 */
    private LocalDate intendedAdmissionDate;
}
