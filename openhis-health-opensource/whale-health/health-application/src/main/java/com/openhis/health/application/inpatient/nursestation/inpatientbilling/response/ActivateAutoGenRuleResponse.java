/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-ARG-06：启用自动计费配置响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "启用自动计费配置响应")
public class ActivateAutoGenRuleResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
