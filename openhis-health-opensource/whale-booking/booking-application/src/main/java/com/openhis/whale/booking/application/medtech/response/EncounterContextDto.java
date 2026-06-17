package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊上下文 DTO（医技预约）。
 */
@Getter
@Setter
public class EncounterContextDto {

    private Long encounterId;
    private String encounterBusNo;
    private Long patientId;
    private String patientName;
    private Long organizationId;
    private String organizationName;
}
