/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.result;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class OutpatientBookingQueryExtensionOut {

    private Long appointmentId;
    private String appointmentBusNo;
    private Long slotId;
    private Long organizationId;
    private Long practitionerId;
    private Long healthcareServiceId;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private String organizationName;
    private String practitionerName;
    private String serviceTypeName;
    private String statusEnum;
    private Long encounterId;
}
