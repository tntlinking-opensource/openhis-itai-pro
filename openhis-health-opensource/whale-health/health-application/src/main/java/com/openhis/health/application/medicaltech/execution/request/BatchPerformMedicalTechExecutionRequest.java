/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.execution.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.medicaltech.execution.dto.MedicalTechPerformItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量医技执行请求（AS-MT-EXE-001 IF-MT-EXE-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量医技执行请求")
public class BatchPerformMedicalTechExecutionRequest {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 就诊类别 scope。 */
    @Schema(description = "就诊类别 scope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterScope;

    /** 诊疗勾选项。 */
    @Schema(description = "诊疗勾选项")
    private List<MedicalTechPerformItemDto> serviceItems;

    /** 耗材勾选项。 */
    @Schema(description = "耗材勾选项")
    private List<MedicalTechPerformItemDto> deviceItems;

    /** 自动扣库开启时可指定耗材房。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材房库位主键")
    private Long deviceLocationId;
}
