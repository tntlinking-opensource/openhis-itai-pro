/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 现场挂号占当日 Slot（模式 C）。 */
@Getter
@Setter
public class OutpatientWalkInSlotAllocateCommand {

    private Long organizationId;
    private Long practitionerId;
    private Long healthcareServiceId;
    private LocalDate registrationDate;
    private Long patientId;
    private Long operatorId;
    private Long slotId;
}
