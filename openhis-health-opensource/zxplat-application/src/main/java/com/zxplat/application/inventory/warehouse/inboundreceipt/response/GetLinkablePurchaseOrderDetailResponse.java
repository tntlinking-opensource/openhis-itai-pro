/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.response;

import com.zxplat.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptDetailLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 从已同意订货单拉取的进货草稿预填数据（供新建进货单表单绑定）。
 *
 * <p>{@code originalBusNo} 与 {@code purchaseOrderBusNo} 相同，均指向来源订货单号；
 * {@code sourceLocationId}/{@code purposeLocationId} 默认均为订货目的药库（药库进货双端点同库位）。</p>
 */
@Getter
@Setter
public class GetLinkablePurchaseOrderDetailResponse {

    /** 来源订货单号（写入进货单 {@code original_bus_no}）。 */
    private String originalBusNo;

    /** 来源订货单号（展示用，与 {@link #originalBusNo} 相同）。 */
    private String purchaseOrderBusNo;

    /** 进货源药库（默认取订货目的药库）。 */
    private Long sourceLocationId;

    /** 进货目的药库（与订货 {@code purpose_location_id} 一致）。 */
    private Long purposeLocationId;

    /** 供应商（订货头字段）。 */
    private Long supplierId;

    /** 供应类别持久化码。 */
    private String categoryEnum;

    /** 订货经手人（可改）。 */
    private Long practitionerId;

    /** 从订货明细拷贝的进货行（含产品展示富化；批号/追溯码留空由用户补录）。 */
    private List<InboundReceiptDetailLineDto> lines;
}
