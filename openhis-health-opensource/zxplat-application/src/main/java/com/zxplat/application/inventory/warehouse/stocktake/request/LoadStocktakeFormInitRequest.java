/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stocktake.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 药库盘点页初始化入参（IF-WHS-ST-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "盘点页初始化 IF-WHS-ST-01")
public class LoadStocktakeFormInitRequest {
}
