/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 门诊账单退费执行：原支付主单收口命令。 */
@Getter
@Setter
public class FinalizeOriginalOutpatientPaymentCommand {

    private Long originalPaymentId;
    private String outcomeEnum;
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    private LocalDateTime billDate;
}
