/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2305 住院结算撤销命令。 */
@Data
@Accessors(chain = true)
public class IpRevokeSettleCommand {

    private Long settlementRecordId;
    private Long paymentId;
    private Long preSettleId;
    private String setlId;
    private Long operatorId;
    private Long tenantId;
}
