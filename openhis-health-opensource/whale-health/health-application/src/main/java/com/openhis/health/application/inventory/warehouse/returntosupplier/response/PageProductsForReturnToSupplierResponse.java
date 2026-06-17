/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.response;

import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ProductDefinitionItemDto;
import com.openhis.health.module.common.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageProductsForReturnToSupplierResponse extends PagedResult<ProductDefinitionItemDto> {
}
