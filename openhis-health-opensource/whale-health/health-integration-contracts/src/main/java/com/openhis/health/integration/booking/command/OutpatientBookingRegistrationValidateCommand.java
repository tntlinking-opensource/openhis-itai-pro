/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.command;

import lombok.Getter;
import lombok.Setter;

/** IF-REG-13 预约转挂号校验命令。 */
@Getter
@Setter
public class OutpatientBookingRegistrationValidateCommand {

    private Long appointmentId;
    private Long organizationId;
    private Long practitionerId;
    private Long healthcareServiceId;
    private Long patientId;
}
