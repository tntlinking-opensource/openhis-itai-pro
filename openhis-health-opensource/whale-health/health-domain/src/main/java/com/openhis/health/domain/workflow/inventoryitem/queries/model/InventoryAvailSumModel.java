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

package com.openhis.health.domain.workflow.inventoryitem.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 单库位+物品可用库存汇总行（全批累加）。
 */
@Getter
@Setter
public class InventoryAvailSumModel {
    /** 库位主键。 */
    private Long locationId;
    /** 物品定义主键。 */
    private Long itemId;
    /** 可用库存总量。 */
    private BigDecimal quantity;
}
