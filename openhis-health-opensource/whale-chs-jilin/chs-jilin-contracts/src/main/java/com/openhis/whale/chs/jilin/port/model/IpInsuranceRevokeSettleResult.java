/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2305 住院结算撤销结果。 */
@Data
@Accessors(chain = true)
public class IpInsuranceRevokeSettleResult {

    private Long settlementRecordId;
    private String setlId;
    private String mdtrtId;
}
