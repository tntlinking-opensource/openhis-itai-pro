/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stocktake.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 盘点单详情入参（IF-WHS-ST-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "盘点单详情 IF-WHS-ST-03")
public class GetStocktakeDetailRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
