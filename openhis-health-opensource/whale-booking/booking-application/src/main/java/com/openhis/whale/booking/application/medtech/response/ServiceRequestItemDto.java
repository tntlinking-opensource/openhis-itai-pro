package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 可预约医嘱行 DTO。
 */
@Getter
@Setter
public class ServiceRequestItemDto {

    private Long serviceRequestId;
    private String serviceRequestBusNo;
    private String activityName;
    private String statusView;
    private Boolean bookedFlag;
    private Long appointmentId;
}
