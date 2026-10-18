/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 审批驳回请求（IF-PRC-14）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "审批驳回请求", requiredProperties = {"busNo", "approveComment"})
public class RejectPriceAdjustmentRequest {

    /** 调价单号。 */
    @NotBlank(message = "调价单号不能为空")
    @Schema(description = "调价单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 驳回原因。 */
    @NotBlank(message = "驳回原因不能为空")
    @Schema(description = "驳回原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String approveComment;
}
