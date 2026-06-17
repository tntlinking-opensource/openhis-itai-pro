/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.response;

import com.openhis.health.application.inventory.pharmacy.returnupstream.dto.ProductDefinitionItemDto;
import com.openhis.health.module.common.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageProductsForReturnUpstreamResponse extends PagedResult<ProductDefinitionItemDto> {
}
