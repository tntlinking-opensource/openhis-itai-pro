/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
