/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.stocktake.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 撤回盘点审批入参（IF-PHA-ST-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "撤回盘点审批 IF-PHA-ST-07")
public class WithdrawPharmacyStocktakeApprovalRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
