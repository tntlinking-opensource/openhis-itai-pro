/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 按科室采购申请单号拉取订货草稿预填数据（IF-WHS-PO-13）。
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#getLinkableDeptPurchaseRequestDetail(GetLinkableDeptPurchaseRequestDetailRequest)}。</p>
 */
@Getter
@Setter
public class GetLinkableDeptPurchaseRequestDetailRequest {

    /** 已同意的科室采购申请业务单号（写入订货单 {@code original_bus_no}）。 */
    private String originalBusNo;
}
