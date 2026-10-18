/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.stocktake.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 删除盘点单出参（IF-PHA-ST-10）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "删除盘点单 IF-PHA-ST-10")
public class RemovePharmacyStocktakeResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
}
