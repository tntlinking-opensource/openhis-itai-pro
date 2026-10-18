/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stocktake.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 编辑盘点单出参（IF-WHS-ST-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "编辑盘点单 IF-WHS-ST-05")
public class UpdateStocktakeResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
