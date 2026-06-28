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

package com.openhis.health.domain.custom.priceadjustment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 提交前库存锁定预览行（BR-PRC-LOCK-008）。
 *
 * <p>应用层 / module.querysupport 组装；对应将参与 {@code inventory_lock} 的库存行，供 UI 防误锁确认。</p>
 */
@Getter
@Setter
public class PriceAdjustmentLockPreviewQueryModel {

    /** 库存项主键 {@code wkf_inventory_item.id}。 */
    private Long inventoryId;

    /** 项目物理表（与本单主行一致）。 */
    private String itemTable;

    /** 项目定义主键。 */
    private Long itemId;

    /** 批号。 */
    private String lotNumber;

    /** 库位 {@code location_id}。 */
    private Long locationId;

    /** 账面可用数量（拆零）。 */
    private BigDecimal quantity;

    /** 是否已被其他 {@code bus_no} 锁定；为 true 时提交将整单失败。 */
    private Boolean lockedByOtherBusNo;
}
