/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2208 门诊结算撤销结果 */
@Data
@Accessors(chain = true)
public class OpRevokeSettleResult {

    private Long settlementRecordId;
    private String setlId;
    private String mdtrtId;
}
