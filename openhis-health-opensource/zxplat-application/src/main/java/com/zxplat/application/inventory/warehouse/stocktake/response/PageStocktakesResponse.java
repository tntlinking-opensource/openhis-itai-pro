/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stocktake.response;

import com.zxplat.application.inventory.warehouse.stocktake.dto.StocktakeListRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 盘点单分页列表出参（IF-WHS-ST-02）。
 */

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class PageStocktakesResponse extends PagedResult<StocktakeListRowDto> {
}
