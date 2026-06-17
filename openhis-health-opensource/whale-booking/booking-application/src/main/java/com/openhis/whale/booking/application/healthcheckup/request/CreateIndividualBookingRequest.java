package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateIndividualBookingRequest {

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
