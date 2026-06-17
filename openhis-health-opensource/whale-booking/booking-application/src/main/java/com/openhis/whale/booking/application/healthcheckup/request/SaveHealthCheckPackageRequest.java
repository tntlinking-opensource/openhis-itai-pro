package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 保存体检套餐请求（9.1）。
 */
@Getter
@Setter
public class SaveHealthCheckPackageRequest {

    private Long id;

    private Long organizationId;

    private String packageCode;

    @NotBlank
    private String packageName;

    private String applicableGender;

    private Integer applicableAgeMin;

    private Integer applicableAgeMax;

    private BigDecimal estimatedAmount;

    private Boolean activeFlag;

    private List<HealthCheckPackageItemRequest> items = new ArrayList<>();
}
