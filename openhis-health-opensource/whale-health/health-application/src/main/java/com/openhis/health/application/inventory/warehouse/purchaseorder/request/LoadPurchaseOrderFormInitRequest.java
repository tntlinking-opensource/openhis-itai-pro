/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>订货页初始化（IF-WHS-PO-01）：下拉含药库（首项「全部」由 AppService 注入）、供应商、库管员、状态与物品表类型。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#loadPurchaseOrderFormInit(LoadPurchaseOrderFormInitRequest)}。</p>
 */
@Getter
@Setter
public class LoadPurchaseOrderFormInitRequest {
}
