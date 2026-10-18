/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 撤回药库退供应商单审批请求（IF-WHS-RT-07）。
 */
@Getter
@Setter
@Schema(description = "撤回退供应商审批 IF-WHS-RT-07")
public class WithdrawReturnToSupplierApprovalRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
