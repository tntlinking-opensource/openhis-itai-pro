/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * IF-REG-12：挂号台加载有效门诊预约检索条件。
 */
@Getter
@Setter
public class ActiveOutpatientBookingLookupQuery {

    /** 患者主键。 */
    private Long patientId;

    /** 预约单号（busNo）。 */
    private String appointmentBusNo;

    /** 挂号自然日；默认当日。 */
    private LocalDate registrationDate;
}
