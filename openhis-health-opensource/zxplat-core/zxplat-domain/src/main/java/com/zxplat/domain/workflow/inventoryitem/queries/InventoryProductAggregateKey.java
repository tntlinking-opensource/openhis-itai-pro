/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.queries;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 库存预警筛选键：全院品种维度（物品表 + 物品定义主键，不含库房）。
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class InventoryProductAggregateKey {

    /** 物品定义表名（如 {@code med_medication_definition}）。 */
    private final String itemTable;

    /** 物品定义主键。 */
    private final Long itemId;
}
