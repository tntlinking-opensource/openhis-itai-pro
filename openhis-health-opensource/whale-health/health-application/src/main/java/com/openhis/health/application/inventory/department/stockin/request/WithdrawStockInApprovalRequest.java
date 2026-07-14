/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回科室入库审批请求（IF-DEPT-IN-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回科室入库审批 IF-DEPT-IN-07", requiredProperties = {"busNo"})
public class WithdrawStockInApprovalRequest {

    @NotBlank(message = "busNo 不能为空")
    /** 业务单号。 */
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
