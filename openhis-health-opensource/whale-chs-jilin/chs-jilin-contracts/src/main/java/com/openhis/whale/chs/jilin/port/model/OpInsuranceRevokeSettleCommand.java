/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2208 门诊结算撤销命令。 */
@Data
@Accessors(chain = true)
public class OpInsuranceRevokeSettleCommand {

    private Long settlementRecordId;
    private Long paymentId;
    private Long preSettleId;
    private String setlId;
}
