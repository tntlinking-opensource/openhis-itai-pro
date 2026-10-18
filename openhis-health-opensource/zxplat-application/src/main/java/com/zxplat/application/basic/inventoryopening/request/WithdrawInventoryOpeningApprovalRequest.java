/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回期初审批请求（IF-BAS-INV-OPN-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回期初审批", requiredProperties = {"busNo"})
public class WithdrawInventoryOpeningApprovalRequest {

    /** 业务单号。 */
    @Schema(description = "单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
