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
 * IF-IP-DOR-06：删除住院就诊诊断请求（AS-IP-DOR-002）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-删除就诊诊断请求")
public class DeleteEncounterDiagnosisRequest {

    /** 就诊诊断行主键（adm_encounter_diagnosis.id）。 */
    @NotNull(message = "诊断标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊诊断行主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterDiagnosisId;

    /** 当前就诊主键（用于解除关联与上下文校验）。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /**
     * 患者主键（门控必填；住院删除接口可不传）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识；删除时用于就诊一致性校验")
    private Long patientId;
}
