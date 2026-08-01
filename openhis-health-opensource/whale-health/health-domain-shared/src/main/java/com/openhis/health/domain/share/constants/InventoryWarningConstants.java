/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.math.BigDecimal;

/**
 * 库存明细与订货快捷预警：系统设置项名、默认阈值与筛选模式码。
 *
 * <p>明细工作台使用 {@link #WARNING_MODE_EXPIRE}/{@link #WARNING_MODE_LOW_STOCK}；
 * 订货快捷另含 {@link #WARNING_FILTER_ALL}（AS-INV-WHS-001 IF-WHS-PO-14）。</p>
 */
public interface InventoryWarningConstants {

    /** 系统设置项：效期预警天数（剩余天数 ≤ 配置值）。 */
    String SETTING_INVENTORY_EXPIRE_WARNING_DAYS = "inventoryExpireWarningDays";

    /** 系统设置项：库存预警包装单位数量上限（全院品种各库房合计后 &lt; 配置值；药品/耗材目录未维护警戒线时回落本项）。 */
    String SETTING_INVENTORY_MIN_PACKAGE_QUANTITY_WARNING = "inventoryMinPackageQuantityWarning";

    /** 未配置效期预警天数时的默认值（与 V10 种子一致）。 */
    int DEFAULT_EXPIRE_WARNING_DAYS = 90;

    /** 未配置库存预警包装数时的默认值（目录未维护警戒线时回落）。 */
    BigDecimal DEFAULT_MIN_PACKAGE_QUANTITY_WARNING = new BigDecimal("30");

    /** 工作台 / 列表跳转：效期预警模式。 */
    String WARNING_MODE_EXPIRE = "expire";

    /** 工作台 / 列表跳转：库存预警模式。 */
    String WARNING_MODE_LOW_STOCK = "lowStock";

    /** 订货快捷预警筛：效期 + 低储并集（AS-INV-WHS-001 IF-WHS-PO-14）。 */
    String WARNING_FILTER_ALL = "all";
}
