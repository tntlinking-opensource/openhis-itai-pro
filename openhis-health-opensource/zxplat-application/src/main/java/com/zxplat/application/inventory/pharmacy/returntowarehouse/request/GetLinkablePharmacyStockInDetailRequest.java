/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.returntowarehouse.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 按药房入库单号拉取退库草稿预填数据（IF-PHA-RTW-13，对偶药库退货 IF-WHS-RT-13）。
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.pharmacy.returntowarehouse.service.InventoryPharmacyReturnToWarehouseAppService#getLinkablePharmacyStockInDetail(GetLinkablePharmacyStockInDetailRequest)}。</p>
 */
@Getter
@Setter
public class GetLinkablePharmacyStockInDetailRequest {

    /** 入库单号 busNo。 */
    @Schema(description = "入库单号 busNo")
    private String busNo;

    /**
     * 可选源药房门禁：非空时须与入库单头 {@code purpose_location_id} 一致且落在当前用户可见药房集合。
     */
    @Schema(description = "源药房 sourceLocationId")
    private Long sourceLocationId;

    /**
     * 修订退库单时传入本单 {@code busNo}，计算可退余量时排除本单已占用量。
     */
    @Schema(description = "排除的退库单号 excludeReturnBusNo")
    private String excludeReturnBusNo;
}
