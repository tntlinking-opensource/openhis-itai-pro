/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 药库库存应用层筛选占位字面量（列表「全部药库」、管辖 Gate 无可见库位等）。
 *
 * <p>禁止在 {@code *AppService} 内重复声明；进货、出库、退库、盘点、损益等列表接口共用。</p>
 */
public interface InventoryWarehouseConstants {

    /**
     * 列表筛选「全部药库」时 {@code sourceLocationId}/{@code purposeLocationId} 占位（0 非真实库位主键）。
     * 前端传此值表示不按单一药库收窄，由服务端替换为当前用户可见药库 id 集合。
     */
    long ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID = 0L;

    /**
     * 管辖 Gate 下无可见药库时，列表查询 {@code sourceLocationIds}/{@code purposeLocationIds} 占位 id，避免误查全表。
     */
    long LIST_FILTER_NO_VISIBLE_WAREHOUSE_PLACEHOLDER_ID = -1L;
}
