/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2208 门诊结算撤销命令。 */
@Data
@Accessors(chain = true)
public class OpRevokeSettleCommand {

    private Long settlementRecordId;
    private Long paymentId;
    private Long preSettleId;
    private String setlId;
    private Long operatorId;
    private Long tenantId;
}
