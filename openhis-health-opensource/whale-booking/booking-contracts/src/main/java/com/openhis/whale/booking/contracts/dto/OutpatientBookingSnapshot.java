package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 门诊预约快照，供挂号台 IF-REG-12 预填。 */
@Getter
@Setter
public class OutpatientBookingSnapshot {

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
