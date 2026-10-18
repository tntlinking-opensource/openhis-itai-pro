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
 * 拉取 LIS/PACS 结果请求（IF-PEIS-DES-08）。
 */
@Data
public class ProjectResultsRequest {

    /** 服务申请主键。 */
    @Schema(description = "服务申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;
}
