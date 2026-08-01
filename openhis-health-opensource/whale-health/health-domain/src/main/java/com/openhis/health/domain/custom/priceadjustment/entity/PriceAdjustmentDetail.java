/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 调价批号明细行（DM-CUS-011 v2，表 {@code cus_price_adjustment_detail}）。
 *
 * <p>药品耗材：按<strong>库房+批号</strong>多行快照数量与双侧调前价（{@code location_id} 可空）；
 * 诊疗：一对一仅存调前售价（占位批号 {@code -}、数量 1、loc 空）。</p>
 * <p>{@code location_id} 仅供查询/日结按库过滤；盈亏公式与审批写价不依赖本字段。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_price_adjustment_detail")
public class PriceAdjustmentDetail extends BaseAuditedEntity {

    /** 调价项目行主键。 */
    @Column(name = "adjustment_id", nullable = false)
    private Long adjustmentId;

    /**
     * 库位主键；有库存时落库，诊疗/未入库等场景可空。
     */
    @Column(name = "location_id")
    private Long locationId;

    /** 批号。 */
    @Column(name = "lot_number", nullable = false)
    private String lotNumber;

    /** 影响数量（拆零；有 loc 时为本库该批数量）。 */
    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    /** 调前进价。 */
    @Column(name = "old_purchase_amount")
    private BigDecimal oldPurchaseAmount;

    /** 调前售价。 */
    @Column(name = "old_selling_amount")
    private BigDecimal oldSellingAmount;

    /** 进价盈亏。 */
    @Column(name = "purchase_impact")
    private BigDecimal purchaseImpact;

    /** 售价盈亏。 */
    @Column(name = "selling_impact")
    private BigDecimal sellingImpact;

    /**
     * 按主行目标价重算盈亏（BR-PRC-013）。
     *
     * @param newPurchaseAmount 目标进价，空则不算进价盈亏
     * @param newSellingAmount  目标售价，空则不算售价盈亏
     */
    public void recalculateImpact(BigDecimal newPurchaseAmount, BigDecimal newSellingAmount) {
        if (newPurchaseAmount != null && this.oldPurchaseAmount != null && this.quantity != null) {
            BigDecimal delta = newPurchaseAmount.subtract(this.oldPurchaseAmount);
            this.purchaseImpact = delta.multiply(this.quantity);
        } else {
            this.purchaseImpact = null;
        }
        if (newSellingAmount != null && this.oldSellingAmount != null && this.quantity != null) {
            BigDecimal delta = newSellingAmount.subtract(this.oldSellingAmount);
            this.sellingImpact = delta.multiply(this.quantity);
        } else {
            this.sellingImpact = null;
        }
    }
}
