package com.openhis.whale.booking.contracts.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 现场挂号占当日 Slot（模式 C）。 */
@Getter
@Setter
public class WalkInSlotAllocateCommand {

    private Long organizationId;
    private Long practitionerId;
    private Long healthcareServiceId;
    private LocalDate registrationDate;
    private Long patientId;

    /** 操作人（占号审计）。 */
    private Long operatorId;

    /** 指定占用的号源主键；为空时取当日首个可用号源。 */
    private Long slotId;
}
