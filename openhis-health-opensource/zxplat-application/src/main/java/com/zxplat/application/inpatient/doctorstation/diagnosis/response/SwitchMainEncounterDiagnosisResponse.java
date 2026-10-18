/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院医生站：切换就诊主诊断响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-切换就诊主诊断响应")
public class SwitchMainEncounterDiagnosisResponse {

    /** 是否成功。 */
    @Schema(description = "切换是否成功")
    private Boolean success;

    /** 面向用户的提示文案。 */
    @Schema(description = "操作结果提示")
    private String message;
}
