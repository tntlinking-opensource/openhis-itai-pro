/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊医保正式结算回执摘要（{@code yb_settlement_record} 只读视图） */
@Data
@Accessors(chain = true)
public class OpSettleRecordResult {

    private Long settlementRecordId;
    private Long paymentId;
    private Long preSettleId;
    private Long contractId;
    private String chrgBchno;
    private String setlId;
    private String mdtrtId;
    private String msgid2207;
}
