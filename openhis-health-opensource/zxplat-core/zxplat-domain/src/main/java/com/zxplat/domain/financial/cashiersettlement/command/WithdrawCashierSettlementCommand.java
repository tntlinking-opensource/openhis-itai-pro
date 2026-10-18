/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤回交账命令（仅 SUBMITTED）。
 */
@Getter
@Setter
public class WithdrawCashierSettlementCommand {

    /** 交账头主键。 */
    private Long settlementId;

    /** 操作人（须为提交人或具备同等权限，应用 Gate 校验）。 */
    private Long operatorId;
}
