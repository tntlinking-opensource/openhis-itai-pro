/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.valueobject;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存匹配键：业务定位以 <strong>五元组</strong>
 * （库位、物品表、物品主键、批号字面值、供应商）为准（DM-WKF-008）。
 *
 * <p>读侧 {@link com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService#findFirstIdByInventoryMatchKey}
 * 在 {@link #supplierId} 非空时按五元组收窄；为空时忽略数量已清零的历史行，同批多行按 FEFO 择首条有量行，不再因多供应商拒绝定位。
 * 出库扣减须走 {@code allocateAndDecrease} 拆分，禁止把整笔数量砸在这一条上。</p>
 */
@Getter
@Setter
public class InventoryMatchKey {

    /** 供应商标识；入账/加量定位时须非空，方可拆开同批号不同供应商账面。 */
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
