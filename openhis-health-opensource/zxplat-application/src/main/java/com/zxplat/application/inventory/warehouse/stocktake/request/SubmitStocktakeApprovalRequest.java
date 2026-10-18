/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stocktake.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交盘点审批入参（IF-WHS-ST-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交盘点审批 IF-WHS-ST-06")
public class SubmitStocktakeApprovalRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
