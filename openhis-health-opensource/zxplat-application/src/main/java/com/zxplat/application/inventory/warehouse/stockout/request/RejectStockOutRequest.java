/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stockout.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批驳回药库出库请求（IF-WHS-SO-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批驳回药库出库 IF-WHS-SO-09", requiredProperties = {"busNo"})
public class RejectStockOutRequest {

    /** 业务单号。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 驳回原因码，可空。 */
    @Schema(description = "驳回原因码，可空")
    private String changeReasonCode;

    /** 驳回原因说明，可空。 */
    @Schema(description = "驳回原因说明，可空")
    private String changeReason;
}
