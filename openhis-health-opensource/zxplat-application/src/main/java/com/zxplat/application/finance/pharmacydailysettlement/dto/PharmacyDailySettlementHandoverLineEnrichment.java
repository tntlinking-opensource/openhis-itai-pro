/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.pharmacydailysettlement.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药房交账弹窗单行 enrich 快照（品规展示 + 进/售行额）。
 *
 * <p>由 {@code PharmacyDailySettlementPricingSupport#buildHandoverLineEnrichments} 产出，
 * 供弹窗明细装配，口径与主区品规读价一致。</p>
 */
@Getter
@Setter
public class PharmacyDailySettlementHandoverLineEnrichment {

    /** 药品目录业务编码。 */
    private String medicationBusNo;

    /** 药品名称。 */
    private String medicationName;

    /** 规格。 */
    private String specification;

    /** 生产厂家。 */
    private String manufacturer;

    /** 计量单位 code（{@code unit_code}）。 */
    private String unitCode;

    /** 进价行额（退药为负）。 */
    private BigDecimal purchaseAmount;

    /** 售价行额（退药为负）。 */
    private BigDecimal saleAmount;

    /** 进价单价。 */
    private BigDecimal purchaseUnitPrice;

    /** 售价单价。 */
    private BigDecimal saleUnitPrice;
}
