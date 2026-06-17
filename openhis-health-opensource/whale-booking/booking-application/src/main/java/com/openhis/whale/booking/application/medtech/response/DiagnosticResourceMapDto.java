package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 医技资源映射 DTO（7.7）。
 */
@Getter
@Setter
public class DiagnosticResourceMapDto {

    private Long id;

    private Long organizationId;

    private Long activityDefinitionId;

    private String serviceCategoryCode;

    private Long scheduleId;

    private Long locationId;

    private Long deviceId;

    private String resourceName;

    private Boolean activeFlag;
}
