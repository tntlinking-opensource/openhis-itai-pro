package com.openhis.whale.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RescheduleBookingRequest {

    @NotNull
    private Long appointmentId;

    @NotNull
    private Long newSlotId;

    private Long destinationLocationId;
}
