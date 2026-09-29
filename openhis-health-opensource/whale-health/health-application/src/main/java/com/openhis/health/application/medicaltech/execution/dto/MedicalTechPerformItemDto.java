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
 * 医技批量执行勾选项（诊疗或耗材，AS-MT-EXE-001 IF-MT-EXE-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技批量执行勾选项")
public class MedicalTechPerformItemDto {

    /** 请求主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;

    /** 请求来源表持久化名。 */
    @Schema(description = "请求来源表", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestTable;

    /** 耗材发放行主键；诊疗项可空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材发放行主键")
    private Long dispenseId;
}
