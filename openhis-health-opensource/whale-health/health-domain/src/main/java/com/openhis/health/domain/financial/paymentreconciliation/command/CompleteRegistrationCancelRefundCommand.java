/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 退号联动退费完成命令。 */
@Getter
@Setter
public class CompleteRegistrationCancelRefundCommand {

    private Long originalPaymentId;
    private Long reversePaymentId;
    private String outcomeEnum;
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    private LocalDateTime billDate;
}
