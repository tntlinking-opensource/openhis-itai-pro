package com.openhis.whale.booking.application.healthcheckup.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 体检套餐项目请求行。
 */
@Getter
@Setter
public class HealthCheckPackageItemRequest {

    private Long activityDefinitionId;

    private String itemName;

    private Boolean requiredFlag;

    private Integer sortOrder;
}
