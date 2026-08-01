/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.periodbalancedetail.support;

import java.math.BigDecimal;

/**
 * 品种级期间业务包装单价追踪：多批号不一致时标记 mixed，展示层置空。
 */
public class PeriodBalanceDetailPackageUnitPriceTracker {

    /** 当前代表性包装单价。 */
    private BigDecimal packageUnitPrice;

    /** 是否已出现多个不同包装单价。 */
    private boolean mixed;

    /**
     * 合并一行包装单价。
     *
     * @param price 包装单位价
     */
    public void merge(BigDecimal price) {
        if (price == null) {
            return;
        }
        if (this.mixed) {
            return;
        }
        if (this.packageUnitPrice == null) {
            this.packageUnitPrice = price;
            return;
        }
        if (this.packageUnitPrice.compareTo(price) != 0) {
            this.mixed = true;
            this.packageUnitPrice = null;
        }
    }

    /**
     * 解析可展示包装单价；多价混批时返回 null。
     *
     * @return 包装单价或 null
     */
    public BigDecimal resolveForDisplay() {
        if (this.mixed) {
            return null;
        }
        return this.packageUnitPrice;
    }
}
