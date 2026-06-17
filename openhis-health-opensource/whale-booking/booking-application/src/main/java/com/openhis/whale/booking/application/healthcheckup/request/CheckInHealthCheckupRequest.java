package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检到检签到请求（9.9）。
 */
@Getter
@Setter
public class CheckInHealthCheckupRequest {

    @NotNull
    private Long organizationId;

    private Long appointmentId;

    private String appointmentBusNo;

    private Long destinationLocationId;
}
