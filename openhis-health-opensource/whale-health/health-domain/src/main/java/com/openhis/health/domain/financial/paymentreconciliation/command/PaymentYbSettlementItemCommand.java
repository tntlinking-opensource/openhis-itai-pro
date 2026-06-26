/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 支付流水落账时写入的医保结算凭证行。
 */
@Getter
@Setter
public class PaymentYbSettlementItemCommand {

    private String setlId;
    private String chrgBchno;
    private String mdtrtId;
    private Long settlementRecordId;
    private Long accountId;
    /** 默认 {@code CONFIRMED}。 */
    private String status;
}
