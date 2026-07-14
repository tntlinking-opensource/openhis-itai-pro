/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TCM-40：停嘱长期中药方响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "停嘱长期中药方响应")
public class StopLongTermTcmHerbalPrescriptionResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 提示信息。 */
    @Schema(description = "提示信息")
    private String message;
}
