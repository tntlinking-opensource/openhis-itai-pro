/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.chargediscount;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 单条收费明细折扣试算行。
 */
@Getter
@Setter
public class ChargeDiscountLineResult {

    /**
     * 收费明细主键。
     */
    private Long chargeItemId;

    /**
     * 原价（通常为 totalPrice / baseAmount）。
     */
    private BigDecimal originalAmount;

    /**
     * 折后应收（结算 2 位）；未打折等于原价结算刻度。
     */
    private BigDecimal discountedReceivable;

    /**
     * 优惠额（原价−折后）；未打折为 0。
     */
    private BigDecimal discountGap;

    /**
     * 有效折扣率；未打折为空。
     */
    private BigDecimal effectiveRate;

    /**
     * 来源 code（CONFIG / CASHIER）；未打折为空。
     */
    private String settleDiscountSourceEnum;

    /**
     * 是否命中配置并参与打折。
     */
    private boolean discounted;
}
