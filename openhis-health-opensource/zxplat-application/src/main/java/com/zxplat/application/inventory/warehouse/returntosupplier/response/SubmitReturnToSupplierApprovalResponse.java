/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 提交药库退供应商单审批成功响应（IF-WHS-RT-06）。
 */
@Getter
@Setter
@Schema(description = "提交退供应商审批 IF-WHS-RT-06")
public class SubmitReturnToSupplierApprovalResponse {

    /** 成功 success。 */
    @Schema(description = "成功 success")
    private Boolean success;

    /** 单据状态 statusEnum。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;
}
