/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2305 住院结算撤销命令 */
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
