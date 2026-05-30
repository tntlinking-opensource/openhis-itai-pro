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

package com.openhis.health.domain.workflow.inventoryitem.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存项条件查询入参。
 */
@Getter
@Setter
public class InventoryItemQuery {

    /** 项来源表。 */
    private String itemTable;

    /** 项主键。 */
    private Long itemId;

    /** 批号号。 */
    private String lotNumber;

    /** 位置主键。 */
    private Long locationId;

    /** 供应商主键。 */
    private Long supplierId;
}

