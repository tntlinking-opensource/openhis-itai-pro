/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊挂号记录修改科室。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号修改科室请求")
public class ChangeRegistrationDepartmentRequest {

    /** 门诊就诊主键。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 目标门诊科室主键。 */
    @Schema(description = "目标科室ID")
    private Long departmentId;
}
