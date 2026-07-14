/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 调价单盈亏汇总读模型（SUM 子表，BR-PRC-013）。
 */
@Getter
@Setter
public class PriceAdjustmentImpactSummaryQueryModel {

    /** 调价单号。 */
    private String busNo;

    /** 进价盈亏合计 {@code SUM(purchase_impact)}。 */
    private BigDecimal totalPurchaseImpact;

    /** 售价盈亏合计 {@code SUM(selling_impact)}。 */
    private BigDecimal totalSellingImpact;
}
