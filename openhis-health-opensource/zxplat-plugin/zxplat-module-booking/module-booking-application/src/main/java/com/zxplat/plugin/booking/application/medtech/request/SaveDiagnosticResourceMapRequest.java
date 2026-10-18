/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存医技资源映射请求（7.7）。
 */
@Getter
@Setter
public class SaveDiagnosticResourceMapRequest {

    /** 映射主键（更新时传入）。 */
    private Long id;

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 活动定义主键。 */
    private Long activityDefinitionId;

    /** 服务类别编码。 */
    private String serviceCategoryCode;

    /** 日程主键。 */
    private Long scheduleId;

    /** 执行地点主键。 */
    private Long locationId;

    /** 设备主键。 */
    private Long deviceId;

    /** 资源显示名称。 */
    private String resourceName;

    /** 是否启用。 */
    private Boolean activeFlag;
}
