/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.bed.result;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class InpatientBedReservationQueryExtensionOut {

    private Long appointmentId;
    private Long bedLocationId;
    private Long patientId;
    private LocalDate intendedAdmissionDate;
}
