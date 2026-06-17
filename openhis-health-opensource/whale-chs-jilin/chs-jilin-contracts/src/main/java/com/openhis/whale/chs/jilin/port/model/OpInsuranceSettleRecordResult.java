/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊医保正式结算回执摘要（yb_settlement_record）。 */
@Data
@Accessors(chain = true)
public class OpInsuranceSettleRecordResult {

    private Long settlementRecordId;
    private Long paymentId;
    private Long preSettleId;
    private Long contractId;
    private String chrgBchno;
    private String setlId;
    private String mdtrtId;
}
