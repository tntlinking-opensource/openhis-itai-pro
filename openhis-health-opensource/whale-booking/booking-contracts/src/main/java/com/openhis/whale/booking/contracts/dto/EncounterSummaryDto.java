package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊摘要（跨模块读 Encounter 头信息）。
 */
@Getter
@Setter
public class EncounterSummaryDto {

    private Long encounterId;
    private Long patientId;
    private Long organizationId;
    private String busNo;
}
