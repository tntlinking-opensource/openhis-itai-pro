/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stockout.response;

import com.zxplat.application.inventory.warehouse.stockout.dto.ProductDefinitionItemDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 药库出库选品分页响应（IF-WHS-SO-11）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageProductsForStockOutResponse extends PagedResult<ProductDefinitionItemDto> {
}
