/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 全院品种维度库存数量汇总（各库房拆零最小单位合计）。
 */
@Getter
@Setter
public class InventoryProductQuantityAggregateModel {

    /** 物品定义表名。 */
    private String itemTable;

    /** 物品定义主键。 */
    private Long itemId;

    /** 拆零最小单位数量合计。 */
    private BigDecimal totalSplitQuantity;
}
