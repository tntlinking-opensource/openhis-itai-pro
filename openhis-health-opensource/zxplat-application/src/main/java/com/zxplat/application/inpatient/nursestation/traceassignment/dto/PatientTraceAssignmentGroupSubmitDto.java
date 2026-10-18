/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-TRC-04：单汇总组追溯码保存载荷。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总组追溯码保存载荷", requiredProperties = {"groupKey", "dispenseIds"})
public class PatientTraceAssignmentGroupSubmitDto {

    /** 汇总组键。 */
    @NotBlank(message = "汇总组键不能为空")
    @Schema(description = "汇总组键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String groupKey;

    /** 底层发放行 ID 列表。 */
    @NotEmpty(message = "发放行 ID 列表不能为空")
    @Schema(description = "底层发放行 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> dispenseIds;

    /** 追溯码（逗号分隔）。 */
    @Schema(description = "追溯码（逗号分隔）")
    private String traceNo;
}
