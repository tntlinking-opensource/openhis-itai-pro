/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.transferin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回药库调入审批响应（IF-WHS-TI-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回药库调入审批 IF-WHS-TI-07")
public class WithdrawTransferInApprovalResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 撤回后状态 SupplyStatus#getCode。 */
    @Schema(description = "撤回后状态 SupplyStatus#getCode")
    private String statusEnum;
}
