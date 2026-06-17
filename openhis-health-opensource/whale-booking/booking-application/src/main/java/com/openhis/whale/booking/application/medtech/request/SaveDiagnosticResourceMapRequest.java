package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存医技资源映射请求（7.7）。
 */
@Getter
@Setter
public class SaveDiagnosticResourceMapRequest {

    private Long id;

    @NotNull
    private Long organizationId;

    private Long activityDefinitionId;

    private String serviceCategoryCode;

    private Long scheduleId;

    private Long locationId;

    private Long deviceId;

    private String resourceName;

    private Boolean activeFlag;
}
