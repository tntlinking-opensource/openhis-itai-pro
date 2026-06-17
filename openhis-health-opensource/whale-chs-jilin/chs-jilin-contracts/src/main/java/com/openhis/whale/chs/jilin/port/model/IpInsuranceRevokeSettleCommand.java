/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2305 住院结算撤销命令。 */
@Data
@Accessors(chain = true)
public class IpInsuranceRevokeSettleCommand {

    private Long settlementRecordId;
    private Long paymentId;
    private Long preSettleId;
    private String setlId;
}
