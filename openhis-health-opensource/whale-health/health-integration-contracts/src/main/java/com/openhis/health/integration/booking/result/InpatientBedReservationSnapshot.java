/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.result;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 住院床位预约快照（health 集成层）。
 */
@Getter
@Setter
public class InpatientBedReservationSnapshot {

    private Long appointmentId;
    private Long bedLocationId;
    private Long patientId;
    private LocalDate intendedAdmissionDate;
}
