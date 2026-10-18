/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.response;

import com.zxplat.application.inventory.warehouse.purchaseorder.dto.ProductDefinitionItemDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 *
 * <p>订货选品分页响应（IF-WHS-PO-11；B.10.11）。
 * 继承 {@link PagedResult}：{@code pageNo} 为 1-based；{@code content} 为 {@link ProductDefinitionItemDto} 列表；
 * 进价与 {@code unitBands} 由 AppService enrich。</p>
 */
public class PageProductsForPurchaseResponse extends PagedResult<ProductDefinitionItemDto> {
}
