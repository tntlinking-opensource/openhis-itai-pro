/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 按订货单号拉取进货草稿预填数据（IF-WHS-IN-13）。
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#getLinkablePurchaseOrderDetail(GetLinkablePurchaseOrderDetailRequest)}。</p>
 */
@Getter
@Setter
public class GetLinkablePurchaseOrderDetailRequest {

    /** 已同意的药库订货单业务单号。 */
    private String busNo;

    /**
     * 可选目的药库门禁：非空时须与订货单头 {@code purpose_location_id} 一致且落在当前用户可见药库集合。
     */
    private Long purposeLocationId;
}
