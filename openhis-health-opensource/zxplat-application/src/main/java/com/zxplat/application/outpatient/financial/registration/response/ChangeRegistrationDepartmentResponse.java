/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊挂号修改科室结果。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号修改科室响应")
public class ChangeRegistrationDepartmentResponse {

    /** 院内科室是否已改到目标科室。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 已有 2201 时是否已撤销并按新科室重挂。 */
    @Schema(description = "是否已重挂医保挂号")
    private Boolean insurancePlatformChanged;
}
