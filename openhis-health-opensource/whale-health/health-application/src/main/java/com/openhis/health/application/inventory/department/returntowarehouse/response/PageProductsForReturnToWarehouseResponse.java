/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.response;

import com.openhis.health.application.inventory.department.returntowarehouse.dto.ProductDefinitionItemDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 科室退库选品分页响应（IF-DEP-RTW-11；内容为源科室药房库存批号行）。
 */
@Getter
@Setter
public class PageProductsForReturnToWarehouseResponse extends PagedResult<ProductDefinitionItemDto> {
}
