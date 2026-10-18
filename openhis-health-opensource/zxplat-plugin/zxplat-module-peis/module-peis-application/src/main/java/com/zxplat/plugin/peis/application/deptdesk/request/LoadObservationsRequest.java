/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 加载服务申请下观察实例列表请求。
 */
@Data
public class LoadObservationsRequest {

    /** 服务申请主键。 */
    @Schema(description = "服务申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /** 体检项目 AD 主键（SR.activityId 为空时的兜底）。 */
    @Schema(description = "体检项目 AD 主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;
}
