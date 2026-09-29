/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量医技执行响应（AS-MT-EXE-001 IF-MT-EXE-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量医技执行响应")
public class BatchPerformMedicalTechExecutionResponse {

    /** 成功条数（诊疗+耗材合计）。 */
    @Schema(description = "成功条数")
    private Integer successCount;

    /** 汇总提示。 */
    @Schema(description = "汇总提示")
    private String message;
}
