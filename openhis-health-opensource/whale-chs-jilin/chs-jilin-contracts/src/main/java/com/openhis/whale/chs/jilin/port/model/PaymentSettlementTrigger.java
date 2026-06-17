/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HIS 收银结算完成后的医保挂接触发载荷（对应 {@code OutpatientPaymentSettledEvent} / {@code InpatientPaymentSettledEvent}）。
 */
@Getter
@Setter
public class PaymentSettlementTrigger {

    private Long paymentId;
    private Long encounterId;
    private String chargeItemIds;
    private BigDecimal displayAmount;
    private LocalDateTime billDate;
    private Long entererId;
    private boolean inpatient;
    private String ybSettleIds;
}
