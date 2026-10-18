/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院医生站：切换就诊主诊断请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-切换就诊主诊断请求")
public class SwitchMainEncounterDiagnosisRequest {

    /** 拟设为主诊的就诊诊断行主键。 */
    @NotNull(message = "诊断标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊诊断行主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterDiagnosisId;

    /** 当前住院就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键（门控）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识；用于就诊一致性校验")
    private Long patientId;
}
