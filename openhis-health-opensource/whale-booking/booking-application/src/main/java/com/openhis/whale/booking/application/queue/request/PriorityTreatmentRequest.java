package com.openhis.whale.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PriorityTreatmentRequest {

    @NotNull
    private Long appointmentId;

    private Long destinationLocationId;
}
