/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.medicaltech.execution.dto.MedicalTechCancelDeviceItemDto;
import com.zxplat.application.medicaltech.execution.dto.MedicalTechCancelProcedureItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量取消医技执行请求（AS-MT-EXE-001 IF-MT-EXE-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量取消医技执行请求")
public class BatchCancelMedicalTechExecutionRequest {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 就诊类别 scope。 */
    @Schema(description = "就诊类别 scope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** 诊疗取消勾选项（COMPLETED procedureId）。 */
    @Schema(description = "诊疗取消项")
    private List<MedicalTechCancelProcedureItemDto> procedureItems;

    /** 耗材取消勾选项。 */
    @Schema(description = "耗材取消项")
    private List<MedicalTechCancelDeviceItemDto> deviceItems;
}
