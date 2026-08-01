/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 体检单服务申请行（REG-03）。
 */
@Data
public class ServiceRequestLineDto {

    /** 服务申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /** 项目 ActivityDefinition 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 项目显示名。 */
    private String display;

    /** 执行状态编码（ext）。 */
    private String execStatusCode;
}
