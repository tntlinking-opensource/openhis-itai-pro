/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.stocktake.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交盘点审批出参（IF-DEPT-ST-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交盘点审批 IF-DEPT-ST-06")
public class SubmitStocktakeApprovalResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;

    /** 状态 statusEnum。 */
    @Schema(description = "状态 statusEnum")
    private String statusEnum;
}
