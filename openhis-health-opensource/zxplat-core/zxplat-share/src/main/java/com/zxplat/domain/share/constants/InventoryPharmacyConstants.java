/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 药房库存应用层筛选占位字面量（列表「全部药房」、管辖 Gate 无可见库位等）。
 *
 * <p>禁止在 {@code *AppService} 内重复声明；药房盘点、损益等列表接口共用。</p>
 */
public interface InventoryPharmacyConstants {

    /**
     * 列表筛选「全部药房」时 {@code purposeLocationId} 占位（0 非真实库位主键）。
     * 前端传此值表示不按单一药房收窄，由服务端替换为当前用户可见药房 id 集合。
     */
    long ALL_PHARMACIES_LIST_FILTER_LOCATION_ID = 0L;

    /**
     * 管辖 Gate 下无可见药房时，列表查询 {@code purposeLocationIds} 占位 id，避免误查全表。
     */
    long LIST_FILTER_NO_VISIBLE_PHARMACY_PLACEHOLDER_ID = -1L;
}
