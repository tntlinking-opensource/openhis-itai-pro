/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.request;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 按进货单号拉取退货草稿预填数据（IF-WHS-RT-13，对偶 IF-WHS-IN-13）。
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.warehouse.returntosupplier.service.InventoryWarehouseReturnToSupplierAppService#getLinkableGoodsReceiptDetail(GetLinkableGoodsReceiptDetailRequest)}。</p>
 */
@Getter
@Setter
public class GetLinkableGoodsReceiptDetailRequest {
    /** 进货单号 busNo。 */
    @Schema(description = "进货单号 busNo")
    private String busNo;

    /**
     * 可选源药库门禁：非空时须与进货单头 {@code purpose_location_id} 一致且落在当前用户可见药库集合。
     */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;

    /**
     * 修订退货单时传入本单 {@code busNo}，计算可退余量时排除本单已占用量。
     */
    @Schema(description = "排除的退货单号 excludeReturnBusNo")
    private String excludeReturnBusNo;
}
