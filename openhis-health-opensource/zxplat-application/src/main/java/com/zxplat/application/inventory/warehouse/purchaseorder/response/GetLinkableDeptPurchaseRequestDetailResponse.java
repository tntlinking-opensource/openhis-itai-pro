/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.response;

import com.zxplat.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderDetailLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 从已同意科室采购申请拉取的订货草稿预填数据（IF-WHS-PO-13；只读，不写库）。
 *
 * <p>{@code originalBusNo} 与 {@code deptPurchaseRequestBusNo} 相同，均指向来源申请单号；
 * {@code purposeLocationId}/{@code purposeLocationName} 为申请科室药房摘要。</p>
 */
@Getter
@Setter
public class GetLinkableDeptPurchaseRequestDetailResponse {

    /** 来源科室采购申请单号（写入订货单 {@code original_bus_no}）。 */
    private String originalBusNo;

    /** 来源申请单号（展示用，与 {@link #originalBusNo} 相同）。 */
    private String deptPurchaseRequestBusNo;

    /** 申请科室药房位置主键。 */
    private Long purposeLocationId;

    /** 申请科室药房展示名（AppService enrich）。 */
    private String purposeLocationName;

    /** 供应类别持久化码。 */
    private String categoryEnum;

    /** 申请经手人（可改）。 */
    private Long practitionerId;

    /** 从申请明细拷贝的订货预填行（含产品展示富化；无供应商）。 */
    private List<PurchaseOrderDetailLineDto> lines;
}
