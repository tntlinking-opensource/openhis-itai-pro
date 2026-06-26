package com.openhis.whale.booking.application.healthcheckup.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 体检套餐项目请求行。
 */
@Getter
@Setter
public class HealthCheckPackageItemRequest {

    /** 活动定义主键（检查项目）。 */
    private Long activityDefinitionId;

    /** 项目名称。 */
    private String itemName;

    /** 是否必检。 */
    private Boolean requiredFlag;

    /** 排序序号。 */
    private Integer sortOrder;
}
