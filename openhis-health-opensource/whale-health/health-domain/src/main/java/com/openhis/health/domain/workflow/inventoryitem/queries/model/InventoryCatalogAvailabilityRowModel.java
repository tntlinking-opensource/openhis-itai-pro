/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /**
     * 库存行创建时间；与效期一并构成 FIFO 次序（效期升序、创建时间升序）。
     */
    private LocalDateTime createdAt;

    /** 当前批次可用数量。 */
    private BigDecimal quantity;
}
