/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.valueobject;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存匹配键：业务定位以 <strong>五元组</strong>
 * （库位、物品表、物品主键、批号字面值、供应商）为准（DM-WKF-008）。
 *
 * <p>读侧 {@link com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService#findFirstIdByInventoryMatchKey}
 * 在 {@link #supplierId} 非空时按五元组收窄；为空时仅当同批号下至多一行可参与增减的账面时才命中，多供应商并存则拒绝自动择行。</p>
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
