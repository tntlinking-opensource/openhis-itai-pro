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

package com.openhis.health.domain.workflow.inventoryitem.valueobject;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存匹配键：业务定位以 <strong>四元组</strong>（库位、物品表、物品主键、批号字面值）为准（DM-WKF-008）；
 * {@link #supplierId} 可随供应行冗余携带，但读侧 {@link com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService#findFirstIdByInventoryMatchKey} 不按其收窄。
 */
@Getter
@Setter
public class InventoryMatchKey {

    /** 供应商标识。 */
    private Long supplierId;

    /** 产品批号。 */
    private String lotNumber;

    /** 库房标识。 */
    private Long locationId;

    /** 物品定义表名。 */
    private String itemTable;

    /** 物品定义标识。 */
    private Long itemId;
}

