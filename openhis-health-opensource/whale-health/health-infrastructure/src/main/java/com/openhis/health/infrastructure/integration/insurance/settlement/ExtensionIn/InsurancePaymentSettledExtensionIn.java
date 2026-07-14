/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 门诊/住院收银落账成功后的医保挂接触发载荷。 */
@Getter
@Setter
public class InsurancePaymentSettledExtensionIn {

    private Long paymentId;
    private Long encounterId;
    private String chargeItemIds;
    private BigDecimal displayAmount;
    private LocalDateTime billDate;
    private Long entererId;
    private boolean inpatient;
    private List<InsurancePaymentYbSettlementLineExtensionIn> ybSettlements;
}
