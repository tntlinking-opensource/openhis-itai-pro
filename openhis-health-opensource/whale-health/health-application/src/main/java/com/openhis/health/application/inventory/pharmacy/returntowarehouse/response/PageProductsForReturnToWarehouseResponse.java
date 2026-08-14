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
 * 药房退库选品分页响应（IF-PHA-RTW-11，源药房库存批号级）。
 */
@Getter
@Setter
public class PageProductsForReturnToWarehouseResponse extends PagedResult<ProductDefinitionItemDto> {
}
