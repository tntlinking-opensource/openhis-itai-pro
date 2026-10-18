/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TCM-42：按方改中药开立时间响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按方改中药开立时间响应")
public class ChangeTcmHerbalPrescriptionOpenedTimeResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 提示信息。 */
    @Schema(description = "提示信息")
    private String message;

    /** 实际改写的药味行数。 */
    @Schema(description = "改写行数")
    private Integer changedLineCount;
}
