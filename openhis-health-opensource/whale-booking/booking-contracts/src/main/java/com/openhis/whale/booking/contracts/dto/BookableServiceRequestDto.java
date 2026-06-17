package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 可预约医技医嘱摘要。
 */
@Getter
@Setter
public class BookableServiceRequestDto {

    private Long serviceRequestId;
    private String serviceRequestBusNo;
    private String activityName;
    private Long encounterId;
    private Long patientId;
    private Long activityId;
    private String categoryEnum;
    private String statusEnum;
    private String reasonText;
}
