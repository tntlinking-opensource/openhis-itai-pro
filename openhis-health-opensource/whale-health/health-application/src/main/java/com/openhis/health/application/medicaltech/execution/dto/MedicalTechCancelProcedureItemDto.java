/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.execution.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医技取消执行·诊疗勾选项（AS-MT-EXE-001 IF-MT-EXE-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技取消执行诊疗项")
public class MedicalTechCancelProcedureItemDto {

    /** 被对冲的 COMPLETED 执行记录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "被对冲 procedureId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long procedureId;
}
