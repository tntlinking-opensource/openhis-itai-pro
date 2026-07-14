/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import lombok.Data;

/**
 * 住院出院财务全结命令：将就诊由待结算推进为已结算（DM-ADM-002 §6.1.23）。
 */
@Data
public class CompleteInpatientFinancialSettlementCommand {

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 结算业务时间（可选；缺省由领域服务取当前时间）。 */
    private java.time.LocalDateTime settlementTime;

    /** 操作员主键（可选；审计由应用层补充）。 */
    private Long operatorId;
}
