/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.deptdesk.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 套用常见结果请求（IF-PEIS-DES-03）。
 */
@Data
public class ApplyCommonResultRequest {

    /** 服务申请主键。 */
    @Schema(description = "服务申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /** 常见结果模板主键。 */
    @Schema(description = "常见结果模板主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long commonResultId;

    /** 科室机构主键（权限校验）。 */
    @Schema(description = "科室机构主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptOrgId;

    /** 是否确认 final。 */
    @Schema(description = "是否确认 final")
    private Boolean finalizeFlag;
}
