package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListIndividualPackagesRequest {

    @NotNull
    private Long organizationId;
}
