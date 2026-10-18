/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.appointment.command;

import lombok.Getter;
import lombok.Setter;

/** IF-REG-13 预约转挂号校验命令。 */
@Getter
@Setter
public class BookingRegistrationValidateCommand {

    private Long appointmentId;
    private Long organizationId;
    private Long practitionerId;
    private Long healthcareServiceId;
    private Long patientId;
}
