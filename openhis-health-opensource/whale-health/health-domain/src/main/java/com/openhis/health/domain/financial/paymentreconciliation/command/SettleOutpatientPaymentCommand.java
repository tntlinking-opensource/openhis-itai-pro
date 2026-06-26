/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** 门诊支付结算落账命令。 */
@Getter
@Setter
public class SettleOutpatientPaymentCommand {
    private Long paymentId;
    private String outcomeEnum;
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    private LocalDateTime billDate;
    private List<PaymentDetailItemCommand> payReceiptDetails;
}
