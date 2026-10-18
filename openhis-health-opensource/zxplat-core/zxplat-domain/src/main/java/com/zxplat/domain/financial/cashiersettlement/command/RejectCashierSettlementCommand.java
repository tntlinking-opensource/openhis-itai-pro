/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 驳回交账命令（仅 SUBMITTED；清挂载；写驳回人与驳回时刻）。
 */
@Getter
@Setter
public class RejectCashierSettlementCommand {

    /** 交账头主键。 */
    private Long settlementId;

    /** 财务驳回人（落库 approver_id，四类日结/审批列表展示）。 */
    private Long approverId;

    /** 驳回原因（可空）。 */
    private String reason;
}
