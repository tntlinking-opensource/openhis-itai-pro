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
import java.time.LocalDateTime;

/**
 * 库存项读模型。
 *
 * <p>分页检索 {@link com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService#queryInventoryItemsByPage}
 * 在药库出库选品等场景需携带批号、效期、物品复合键等扩展列，与 {@code wkf_inventory_item} 行对齐（AS-INV-WHS-003）。</p>
 */
@Getter
@Setter
public class InventoryItemModel {

    /** 主键。 */
    private Long id;

    /** 数量。 */
    private BigDecimal quantity;

    /** 库存状态枚举编码。 */
    private String inventoryStatusEnum;

    /** 物品定义表名。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 物品展示名称（冗余列）。 */
    private String name;

    /** 计量单位编码。 */
    private String unitCode;

    /** 供应商标识。 */
    private Long supplierId;

    /** 批号。 */
    private String lotNumber;

    /** 效期（与库列 {@code expiration_date} 一致）。 */
    private LocalDateTime expirationDate;

    /** 生产日期（与库列 {@code production_date} 一致）。 */
    private LocalDateTime productionDate;

    /** 库房位置主键。 */
    private Long locationId;
}

