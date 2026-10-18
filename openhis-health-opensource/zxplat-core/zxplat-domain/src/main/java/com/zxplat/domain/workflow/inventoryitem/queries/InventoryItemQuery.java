/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.queries;

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
