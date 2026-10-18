/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-08：医生保存就诊诊断（全量批次）请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-保存就诊诊断请求")
public class SaveEncounterDiagnosisRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 诊断批次（完整列表）。 */
    @NotEmpty(message = "诊断批次不能为空")
    @Valid
    /** 诊断批次列表。 */
    @Schema(description = "诊断批次列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SaveEncounterDiagnosisChildRequest> diagnosisChildList;
}
