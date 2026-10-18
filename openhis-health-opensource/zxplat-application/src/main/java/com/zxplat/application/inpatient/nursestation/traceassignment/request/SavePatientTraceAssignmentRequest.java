/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentGroupSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-TRC-04：保存患者追溯码分配入参。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存患者追溯码分配入参", requiredProperties = {"encounterId", "groups"})
public class SavePatientTraceAssignmentRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊 ID 不能为空")
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 待保存汇总组列表。 */
    @NotEmpty(message = "待保存汇总组不能为空")
    @Schema(description = "待保存汇总组列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PatientTraceAssignmentGroupSubmitDto> groups;
}
