/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 保存小项结果请求（IF-PEIS-DES-02）。
 */
@Data
public class SaveObservationRequest {

    /** 服务申请主键。 */
    @Schema(description = "服务申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /** 观察定义主键。 */
    @Schema(description = "观察定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long definitionId;

    /** 科室机构主键（权限校验）。 */
    @Schema(description = "科室机构主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptOrgId;

    /** 数值结果。 */
    @Schema(description = "数值结果")
    private BigDecimal valueQuantity;

    /** 文本结果。 */
    @Schema(description = "文本结果")
    private String valueString;

    /** 解读编码。 */
    @Schema(description = "解读编码")
    private String interpretationCode;

    /** 是否确认 final。 */
    @Schema(description = "是否确认 final")
    private Boolean finalizeFlag;
}
