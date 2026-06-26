/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2208 门诊结算撤销结果。 */
@Data
@Accessors(chain = true)
public class OpRevokeSettleResult {

    private Long settlementRecordId;
    private String setlId;
    private String mdtrtId;
}
