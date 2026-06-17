package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技到检报到请求（7.6）。
 */
@Getter
@Setter
public class CheckInDiagnosticRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long appointmentId;

    private Long destinationLocationId;
}
