/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stockout.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 按请领单号拉取出库草稿预填数据（IF-WHS-SO-13）。
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.warehouse.stockout.service.InventoryWarehouseStockOutAppService#getLinkablePharmacyRequisitionDetail(GetLinkablePharmacyRequisitionDetailRequest)}。</p>
 */
@Getter
@Setter
public class GetLinkablePharmacyRequisitionDetailRequest {

    /** 已同意的药房请领单业务单号。 */
    private String busNo;

    /**
     * 可选源药库门禁：非空时须与请领单头 {@code source_location_id} 一致且落在当前用户可见药库集合。
     */
    private Long sourceLocationId;

    /**
     * 改单时排除本出库单号，避免 BR-PHA-QL-011 将当前单误判为重复引用。
     */
    private String excludeStockOutBusNo;
}
