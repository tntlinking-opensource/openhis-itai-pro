/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.response;

import com.zxplat.application.inventory.warehouse.purchaseorder.dto.PurchaseWarningProductRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订货预警快捷选品分页响应（IF-WHS-PO-14；B.10.11）。
 *
 * <p>继承 {@link PagedResult}：{@code content} 为品种维行（无批号）；由
 * {@link com.zxplat.application.inventory.warehouse.purchaseorder.mapper.InventoryWarehousePurchaseOrderMapper#toPagePurchaseWarningProductsResponse}
 * 装配。</p>
 */
@Schema(description = "订货预警快捷选品分页")
public class PagePurchaseWarningProductsResponse extends PagedResult<PurchaseWarningProductRowDto> {
}
