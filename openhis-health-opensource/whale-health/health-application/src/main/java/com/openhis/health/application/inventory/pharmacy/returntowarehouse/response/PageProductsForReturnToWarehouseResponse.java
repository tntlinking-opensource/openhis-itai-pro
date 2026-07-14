/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.response;

import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.ProductDefinitionItemDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 产品选品分页响应（已废弃，本场景禁止自由选品）。
 */
@Getter
@Setter
public class PageProductsForReturnToWarehouseResponse extends PagedResult<ProductDefinitionItemDto> {
}
