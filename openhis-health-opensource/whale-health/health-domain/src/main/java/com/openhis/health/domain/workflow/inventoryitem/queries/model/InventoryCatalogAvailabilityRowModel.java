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
 * 医嘱开立目录用：单条可用库存行（多物品 id × 多库位批量加载后的行视图）。
 *
 * <p>与 {@link InventoryItemQueryService} 中「可用量读取契约」所列批量路径配套：承载行级事实（含 {@code wkf_inventory_item.id}），供跨库加总前装配。</p>
 */
@Getter
@Setter
public class InventoryCatalogAvailabilityRowModel {

    /** 库存项主键（wkf_inventory_item.id）。 */
    private Long id;

    /** 物品来源表名（与目录 {@code product_table_name} 对齐）。 */
    private String itemTable;

    /** 物品定义主键（与目录 {@code product_definition_id} 对齐）。 */
    private Long itemId;

    /** 库位主键（候选药房/药库位置）。 */
    private Long locationId;

    /** 批号（FIFO 排序键之一）。 */
    private String lotNumber;

    /** 效期截止时刻；空表示未维护效期约束。 */
    private LocalDateTime expirationDate;

    /** 当前批次可用数量。 */
    private BigDecimal quantity;
}
