/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>订货单详情（IF-WHS-PO-03）：按 {@code busNo} 读取同号全部明细行与头字段，含修订用 {@code rowVersion}。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#getPurchaseOrderDetail(GetPurchaseOrderDetailRequest)}。</p>
 */
@Getter
@Setter
public class GetPurchaseOrderDetailRequest {

    /**
     * 订货单业务单号；应用服务门控非空并对首尾空白 {@code trim}。
     */
    private String busNo;
}
