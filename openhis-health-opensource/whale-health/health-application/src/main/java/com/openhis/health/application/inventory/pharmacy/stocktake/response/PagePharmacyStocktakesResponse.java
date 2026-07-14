/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.stocktake.response;

import com.openhis.health.application.inventory.pharmacy.stocktake.dto.PharmacyStocktakeListRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 盘点单分页列表出参（IF-PHA-ST-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "盘点单分页列表 IF-PHA-ST-02")
public class PagePharmacyStocktakesResponse extends PagedResult<PharmacyStocktakeListRowDto> {
}
