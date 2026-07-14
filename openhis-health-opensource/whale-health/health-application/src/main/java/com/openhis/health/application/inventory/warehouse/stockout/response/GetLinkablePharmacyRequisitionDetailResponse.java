/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stockout.response;

import com.openhis.health.application.inventory.warehouse.stockout.dto.StockOutDetailLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 从已同意请领单拉取的出库草稿预填数据（供新建出库单表单绑定）。
 *
 * <p>{@code originalBusNo} 与 {@code requisitionBusNo} 相同，均指向来源请领单号；
 * {@code sourceLocationId} 为药库（扣减端），{@code purposeLocationId} 为药房或科室药房（接收端）。</p>
 */
@Getter
@Setter
public class GetLinkablePharmacyRequisitionDetailResponse {

    /** 来源请领单号（写入出库单 {@code original_bus_no}）。 */
    private String originalBusNo;

    /** 来源请领单号（展示用，与 {@link #originalBusNo} 相同）。 */
    private String requisitionBusNo;

    /** 出库源药库（请领 {@code source_location_id}）。 */
    private Long sourceLocationId;

    /** 出库目的药房或科室药房（请领 {@code purpose_location_id}）。 */
    private Long purposeLocationId;

    /** 供应类别持久化码。 */
    private String categoryEnum;

    /** 请领经手人（可改）。 */
    private Long practitionerId;

    /** 从请领明细拷贝的出库行（含产品展示富化；数量可在保存前调整）。 */
    private List<StockOutDetailLineDto> lines;
}
