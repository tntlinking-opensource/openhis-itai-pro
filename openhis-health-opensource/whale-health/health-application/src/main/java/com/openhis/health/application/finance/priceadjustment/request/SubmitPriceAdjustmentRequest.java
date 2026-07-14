/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交调价审批请求（IF-PRC-10）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交调价审批请求", requiredProperties = {"busNo"})
public class SubmitPriceAdjustmentRequest {

    /** 调价单号。 */
    @NotBlank(message = "调价单号不能为空")
    @Schema(description = "调价单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
