package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建个检预约请求（9.7）。
 */
@Getter
@Setter
public class CreateIndividualHealthCheckupBookingRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long patientId;

    @NotNull
    private Long packageId;

    @NotNull
    private Long slotId;

    private String originChannel;
}
