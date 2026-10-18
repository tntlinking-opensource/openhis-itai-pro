/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量取消医技执行响应（AS-MT-EXE-001 IF-MT-EXE-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量取消医技执行响应")
public class BatchCancelMedicalTechExecutionResponse {

    /** 成功条数（诊疗对冲+耗材回退合计）。 */
    @Schema(description = "成功条数")
    private Integer successCount;

    /** 汇总提示。 */
    @Schema(description = "汇总提示")
    private String message;
}
