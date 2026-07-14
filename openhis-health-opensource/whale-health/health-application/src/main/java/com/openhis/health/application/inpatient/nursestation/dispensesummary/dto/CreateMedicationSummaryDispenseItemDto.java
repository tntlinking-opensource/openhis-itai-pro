/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 生成汇总领药 · 勾选发放行（IF-IP-SUM-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总写路径勾选发放行")
public class CreateMedicationSummaryDispenseItemDto {

    /** 发放行主键。 */
    @NotNull(message = "发放行不能为空")
    @Schema(description = "发放行主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 就诊主键（可见性交叉校验）。 */
    @NotNull(message = "就诊不能为空")
    /** 就诊主键。 */
    @Schema(description = "就诊主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
