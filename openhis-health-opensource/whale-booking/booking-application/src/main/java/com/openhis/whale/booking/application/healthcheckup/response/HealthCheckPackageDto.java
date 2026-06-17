package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class HealthCheckPackageDto {

    private Long id;

    private Long organizationId;

    private String packageCode;

    private String packageName;

    private String applicableGender;

    private Integer applicableAgeMin;

    private Integer applicableAgeMax;

    private BigDecimal estimatedAmount;

    private Boolean activeFlag;
}
