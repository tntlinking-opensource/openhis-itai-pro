/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.math.BigDecimal;

/**
 * 库存明细预警：系统设置项名、默认阈值与列表筛选模式码。
 */
public interface InventoryWarningConstants {

    /** 系统设置项：效期预警天数（剩余天数 ≤ 配置值）。 */
    String SETTING_INVENTORY_EXPIRE_WARNING_DAYS = "inventoryExpireWarningDays";

    /** 系统设置项：库存预警包装单位数量上限（全院品种各库房合计后 &lt; 配置值）。 */
    String SETTING_INVENTORY_MIN_PACKAGE_QUANTITY_WARNING = "inventoryMinPackageQuantityWarning";

    /** 未配置效期预警天数时的默认值。 */
    int DEFAULT_EXPIRE_WARNING_DAYS = 180;

    /** 未配置库存预警包装数时的默认值。 */
    BigDecimal DEFAULT_MIN_PACKAGE_QUANTITY_WARNING = new BigDecimal("30");

    /** 工作台 / 列表跳转：效期预警模式。 */
    String WARNING_MODE_EXPIRE = "expire";

    /** 工作台 / 列表跳转：库存预警模式。 */
    String WARNING_MODE_LOW_STOCK = "lowStock";
}
