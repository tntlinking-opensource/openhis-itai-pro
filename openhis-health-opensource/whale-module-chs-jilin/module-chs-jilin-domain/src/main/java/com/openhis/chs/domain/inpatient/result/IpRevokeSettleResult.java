/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2305 住院结算撤销结果 */
@Data
@Accessors(chain = true)
public class IpRevokeSettleResult {

    private Long settlementRecordId;
    private String setlId;
    private String mdtrtId;
}
