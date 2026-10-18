/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 更正入院科室响应（IF-IP-REG-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "更正入院科室响应")
public class ChangeAdmissionDepartmentResponse {

    /** 院内入院科室已按请求落库。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /**
     * 本次是否已向医保平台发送住院信息变更（2403）。未做 2401 时为 false。
     */
    @Schema(description = "是否已调用医保住院信息变更")
    private Boolean insurancePlatformChanged;
}
