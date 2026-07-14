/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.result;

import lombok.Getter;
import lombok.Setter;

/** 可约号源类型选项（IF-REG-01 收窄）。 */
@Getter
@Setter
public class BookableServiceOption {

    /** 号源类型（HealthcareService）主键。 */
    private Long healthcareServiceId;

    /** 号源类型名称。 */
    private String serviceTypeName;
}
