/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>删除订货单（IF-WHS-PO-10）：按 {@code busNo} 软删整单及明细，仅草稿/撤回等可删态允许。</p>
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#removePurchaseOrder(RemovePurchaseOrderRequest)}。</p>
 */
@Getter
@Setter
public class RemovePurchaseOrderRequest {

    /**
     * 订货单业务单号；应用服务门控非空。
     */
    private String busNo;
}
