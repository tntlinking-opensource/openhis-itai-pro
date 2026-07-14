/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
