package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技改约请求（7.5）。
 */
@Getter
@Setter
public class RescheduleDiagnosticBookingRequest {

    @NotNull
    private Long appointmentId;

    @NotNull
    private Long newSlotId;

    private Long destinationLocationId;
}
