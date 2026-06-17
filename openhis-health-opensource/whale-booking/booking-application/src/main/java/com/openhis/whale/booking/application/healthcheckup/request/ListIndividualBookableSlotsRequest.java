package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ListIndividualBookableSlotsRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long packageId;

    @NotNull
    private LocalDate date;
}
