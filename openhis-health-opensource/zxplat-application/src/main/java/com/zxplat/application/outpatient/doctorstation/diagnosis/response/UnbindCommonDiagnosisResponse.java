/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 解绑个人/科室常用诊断模板响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-解绑常用诊断响应")
public class UnbindCommonDiagnosisResponse {

    /**
     * 是否成功。
     */
    @Schema(description = "是否成功")
    private Boolean success;

    /**
     * 操作结果提示。
     */
    @Schema(description = "操作结果提示")
    private String message;

    /**
     * 本次实际解绑条数。
     */
    @Schema(description = "解绑条数")
    private Integer unboundCount;
}
