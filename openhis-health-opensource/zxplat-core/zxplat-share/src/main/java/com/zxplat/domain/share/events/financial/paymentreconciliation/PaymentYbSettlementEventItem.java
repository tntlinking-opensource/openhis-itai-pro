/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.financial.paymentreconciliation;

import lombok.Getter;
import lombok.Setter;

/** 支付结算事件携带的医保凭证快照。 */
@Getter
@Setter
public class PaymentYbSettlementEventItem {

    /** setl主键（PaymentYbSettlementEventItem）。 */
    private String setlId;
    /** chrgBchno（PaymentYbSettlementEventItem）。 */
    private String chrgBchno;
    /** mdtrt主键（PaymentYbSettlementEventItem）。 */
    private String mdtrtId;
    /** settlementRecord主键（PaymentYbSettlementEventItem）。 */
    private Long settlementRecordId;
    /** account主键（PaymentYbSettlementEventItem）。 */
    private Long accountId;
    /** 状态（PaymentYbSettlementEventItem）。 */
    private String status;
}
