/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
