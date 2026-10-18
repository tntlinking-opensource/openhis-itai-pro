/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.stocktake.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 新建盘点单出参（IF-PHA-ST-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "新建盘点单 IF-PHA-ST-04")
public class CreatePharmacyStocktakeResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
