/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stocktake.response;

import com.openhis.health.application.inventory.warehouse.stocktake.dto.ProductDefinitionItemDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 盘点选品分页出参（IF-WHS-ST-11）。
 */

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class PageProductsForStocktakeResponse extends PagedResult<ProductDefinitionItemDto> {
}
