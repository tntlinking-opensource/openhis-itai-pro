package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ListIndividualPackagesResponse {

    private List<HealthCheckPackageDto> packages;
}
