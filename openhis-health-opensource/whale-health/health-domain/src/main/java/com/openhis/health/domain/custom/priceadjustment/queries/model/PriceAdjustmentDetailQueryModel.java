/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 调价批号明细读模型（对应 {@code cus_price_adjustment_detail} 一行）。
 *
 * <p>盈亏在提交时固化：{@code (目标价 - 调前价) × quantity}。</p>
 * <p>物流可按库房+批号多行；应用层调价单回显须按批号合并，日结按 {@code locationId} 过滤。</p>
 */
@Getter
@Setter
public class PriceAdjustmentDetailQueryModel {

    /** 明细主键。 */
    private Long id;

    /**
     * 库位主键；可空。
     */
    private Long locationId;

    /** 批号 {@code lot_number}。 */
    private String lotNumber;

    /** 影响数量（拆零；有 loc 时为本库该批数量）{@code quantity}。 */
    private BigDecimal quantity;

    /** 调前进价快照 {@code old_purchase_amount}。 */
    private BigDecimal oldPurchaseAmount;

    /** 调前售价快照 {@code old_selling_amount}。 */
    private BigDecimal oldSellingAmount;

    /** 进价盈亏 {@code purchase_impact}。 */
    private BigDecimal purchaseImpact;

    /** 售价盈亏 {@code selling_impact}。 */
    private BigDecimal sellingImpact;
}
