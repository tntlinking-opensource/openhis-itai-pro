/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 撤回退库入库壳单审批请求（IF-WHS-RSI-07，壳单已终态：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "撤回退库入库审批 IF-WHS-RSI-07（已拒绝）")
public class WithdrawReturnStockInApprovalRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
