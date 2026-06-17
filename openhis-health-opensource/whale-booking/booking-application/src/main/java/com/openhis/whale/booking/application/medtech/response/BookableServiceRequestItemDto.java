package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 可预约医嘱行 DTO。
 */
@Getter
@Setter
public class BookableServiceRequestItemDto {

    private Long serviceRequestId;

    private Long encounterId;

    private Long patientId;

    private Long activityId;

    private String categoryEnum;

    private String statusEnum;

    private String reasonText;

    private Boolean bookedFlag;

    private Long appointmentId;
}
