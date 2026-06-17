package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊检索结果（医技预约上下文）。
 */
@Getter
@Setter
public class EncounterSearchResultDto {

    private Long encounterId;
    private String encounterBusNo;
    private Long patientId;
    private String patientName;
    private Long organizationId;
    private String organizationName;
}
