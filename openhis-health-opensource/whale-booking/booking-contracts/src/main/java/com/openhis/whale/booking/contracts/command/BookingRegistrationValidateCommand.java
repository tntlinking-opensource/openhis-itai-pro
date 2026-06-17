package com.openhis.whale.booking.contracts.command;

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
