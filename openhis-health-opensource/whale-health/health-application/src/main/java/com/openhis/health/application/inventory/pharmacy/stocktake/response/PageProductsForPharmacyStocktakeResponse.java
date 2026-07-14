/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.stocktake.response;

import com.openhis.health.application.inventory.pharmacy.stocktake.dto.ProductDefinitionItemDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 盘点选品分页出参（IF-PHA-ST-11）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "盘点选品 IF-PHA-ST-11")
public class PageProductsForPharmacyStocktakeResponse extends PagedResult<ProductDefinitionItemDto> {
}
