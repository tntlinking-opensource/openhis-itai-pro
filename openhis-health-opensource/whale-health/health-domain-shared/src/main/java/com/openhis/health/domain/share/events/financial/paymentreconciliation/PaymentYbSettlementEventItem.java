/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.events.financial.paymentreconciliation;

import lombok.Getter;
import lombok.Setter;

/** 支付结算事件携带的医保凭证快照。 */
@Getter
@Setter
public class PaymentYbSettlementEventItem {

    private String setlId;
    private String chrgBchno;
    private String mdtrtId;
    private Long settlementRecordId;
    private Long accountId;
    private String status;
}
