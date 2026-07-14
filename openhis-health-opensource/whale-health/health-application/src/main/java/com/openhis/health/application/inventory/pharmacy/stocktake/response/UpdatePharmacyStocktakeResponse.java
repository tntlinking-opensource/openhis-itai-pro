/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.stocktake.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 编辑盘点单出参（IF-PHA-ST-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "编辑盘点单 IF-PHA-ST-05")
public class UpdatePharmacyStocktakeResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
