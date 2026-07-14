/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.response;

import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ProductDefinitionItemDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 药库退供应商选品分页响应（IF-WHS-RT-11，已废弃，接口固定拒绝）。
 *
 * @deprecated 药库退货禁止自由选品
 */
@Deprecated
@Getter
@Setter
@Schema(description = "退供应商选品分页 IF-WHS-RT-11（已废弃）")
public class PageProductsForReturnToSupplierResponse extends PagedResult<ProductDefinitionItemDto> {
}
