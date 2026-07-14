/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-TRC-03：批量解析追溯码入参。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量解析追溯码入参", requiredProperties = {"encounterId", "traceCodes", "groupKeys"})
public class ResolveTraceCodesForPatientRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊 ID 不能为空")
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 批量追溯码。 */
    @NotEmpty(message = "追溯码列表不能为空")
    @Schema(description = "批量追溯码", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> traceCodes;

    /** 汇总组键快照。 */
    @NotEmpty(message = "汇总组键不能为空")
    @Schema(description = "汇总组键快照", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> groupKeys;
}
