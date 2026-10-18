/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 审批通过交账命令（仅 SUBMITTED；不改挂载）。
 */
@Getter
@Setter
public class ApproveCashierSettlementCommand {

    /** 交账头主键。 */
    private Long settlementId;

    /** 审批人。 */
    private Long approverId;
}
