/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-08A：追加个人/科室常用模板绑定响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-绑定个人/科室常用诊断响应")
public class BindCommonDiagnosisResponse {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "操作结果提示")
    private String message;

    /** 个人常用本次新插入行数（幂等跳过不计）。 */
    @Schema(description = "个人常用新绑定条数")
    private Integer personalBoundCount;

    /** 科室常用本次新插入行数（幂等跳过不计）。 */
    @Schema(description = "科室常用新绑定条数")
    private Integer departmentBoundCount;
}
