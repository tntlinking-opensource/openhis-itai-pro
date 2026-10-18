/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.response;

import com.zxplat.application.inventory.warehouse.returnstockin.dto.ProductDefinitionItemDto;
import com.zxplat.application.shared.pagination.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 退库入库选品分页响应（IF-WHS-RSI-11，禁止自由选品：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "退库入库选品分页 IF-WHS-RSI-11（已拒绝）")
public class PageProductsForReturnStockInResponse extends PagedResult<ProductDefinitionItemDto> {
}
