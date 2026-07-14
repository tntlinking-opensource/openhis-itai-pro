/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2208 门诊结算撤销命令 */
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
