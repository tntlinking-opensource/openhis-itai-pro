/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 撤回药库退供应商单审批成功响应（IF-WHS-RT-07）。
 */
@Getter
@Setter
@Schema(description = "撤回退供应商审批 IF-WHS-RT-07")
public class WithdrawReturnToSupplierApprovalResponse {

    /** 成功 success。 */
    @Schema(description = "成功 success")
    private Boolean success;

    /** 单据状态 statusEnum。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;
}
