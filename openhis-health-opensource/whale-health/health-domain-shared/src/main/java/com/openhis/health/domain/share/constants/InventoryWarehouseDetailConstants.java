/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 药库库存明细筛选相关字面量（与 {@code inventory_range} 术语概念码对齐）。
 *
 * <p>种子见 {@code V58__ter_concept.sql}：{@code 2}→数量等于0；{@code 3}→数量大于0。</p>
 */
public interface InventoryWarehouseDetailConstants {

    /** 库存范围：账面数量等于 0（{@code inventory_range} 概念码 {@code 2}）。 */
    String INVENTORY_RANGE_ZERO_CODE = "2";

    /** 库存范围：账面数量大于 0（{@code inventory_range} 概念码 {@code 3}，页面默认）。 */
    String INVENTORY_RANGE_GT_ZERO_CODE = "3";
}
