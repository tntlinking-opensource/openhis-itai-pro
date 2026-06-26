/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.command;

import com.openhis.health.domain.share.enums.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 按支付主键回写状态与医保结算凭证。 */
@Getter
@Setter
public class UpdatePaymentStatusWithSettleIdsCommand {
    private Long paymentId;
    private PaymentStatus statusEnum;
    private List<PaymentYbSettlementItemCommand> ybSettlements;
    private String outcomeEnum;
}
