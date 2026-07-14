/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回药库入库审批请求（IF-WHS-IN-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回药库入库审批 IF-WHS-IN-07", requiredProperties = {"busNo"})
public class WithdrawInboundReceiptApprovalRequest {

    @NotBlank(message = "busNo 不能为空")
    /** 业务单号。 */
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
