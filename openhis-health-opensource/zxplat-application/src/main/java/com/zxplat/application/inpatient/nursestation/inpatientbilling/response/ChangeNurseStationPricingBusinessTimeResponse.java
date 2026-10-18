/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-PRC-07：修改划价客户可见时间响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "修改划价展示时间响应")
public class ChangeNurseStationPricingBusinessTimeResponse {

    /** 整批成功。 */
    @Schema(description = "整批成功")
    private Boolean success;

    /** 成功改时行数。 */
    @Schema(description = "成功改时行数")
    private Integer processedCount;

    /** 提示文案（可选）。 */
    @Schema(description = "提示文案")
    private String message;
}
