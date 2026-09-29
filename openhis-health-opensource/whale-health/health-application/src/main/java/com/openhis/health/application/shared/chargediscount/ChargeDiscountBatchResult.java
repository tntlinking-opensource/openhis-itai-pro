/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.chargediscount;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一笔收费/结算的折扣试算结果（application.shared；供 OP/IP 共用）。
 *
 * <p>{@link #applied} 为 false 时表示门控关闭或本批未打折，调用方须走原有原价路径。</p>
 */
@Getter
@Setter
public class ChargeDiscountBatchResult {

    /**
     * 是否对本批应用了折扣（false=完全无改，等价于功能未加）。
     */
    private boolean applied;

    /**
     * 折后应收合计（结算 2 位）；未应用时为 0。
     */
    private BigDecimal discountedReceivableTotal = BigDecimal.ZERO;

    /**
     * 优惠合计（原价−折后，结算 2 位）；未应用时为 0。
     */
    private BigDecimal discountGapTotal = BigDecimal.ZERO;

    /**
     * 本批统一有效折扣率；未应用时为空。
     */
    private BigDecimal effectiveRate;

    /**
     * 行级结果；键为 chargeItemId。
     */
    private Map<Long, ChargeDiscountLineResult> lineByChargeItemId = new HashMap<>();

    /**
     * 参与打折的行（有命中并折算）。
     */
    private List<ChargeDiscountLineResult> discountedLines = new ArrayList<>();
}
