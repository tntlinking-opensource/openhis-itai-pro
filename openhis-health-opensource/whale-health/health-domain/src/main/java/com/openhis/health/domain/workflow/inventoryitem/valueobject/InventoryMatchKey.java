/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
