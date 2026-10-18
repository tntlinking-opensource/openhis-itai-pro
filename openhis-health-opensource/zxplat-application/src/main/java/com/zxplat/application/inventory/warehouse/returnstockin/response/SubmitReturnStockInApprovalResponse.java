/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 提交退库入库壳单审批成功响应（IF-WHS-RSI-06，接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "提交退库入库审批 IF-WHS-RSI-06（已拒绝）")
public class SubmitReturnStockInApprovalResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;

    /** 单据状态 statusEnum。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;
}
