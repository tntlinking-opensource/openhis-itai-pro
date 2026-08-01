/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.support;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 发放行账单价视图（历史 ChargeItem 落库单价/行合计，BR-PRICE-SRC-001）。
 *
 * <p>缺账单时字段可空，不得阻断整次查询（BR-IP-PMU-012）。</p>
 */
@Getter
@Setter
public class ChargePricingView {

    /** 账单单价；可空。 */
    private BigDecimal unitPrice;

    /** 账单行合计；可空；退药通常为负。 */
    private BigDecimal totalPrice;
}
