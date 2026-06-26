/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** IF-REG-12：挂号台加载有效预约检索条件。 */
@Getter
@Setter
public class OutpatientBookingLookupCommand {

    private Long patientId;
    private String appointmentBusNo;
    private LocalDate registrationDate;
}
