/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 住院取消结算收口命令。 */
@Getter
@Setter
public class CompleteInpatientSettleCancelRefundCommand {

    private Long originalPaymentId;
    private Long reversePaymentId;
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    private String outcomeEnum;
    private LocalDateTime billDate;
}
