/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.reportgrantext.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 登记报告发放命令（DM-PEIS-005 REP-05）。
 */
@Getter
@Setter
public class RegisterGrantCommand {

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 租户标识。 */
    private Long tenantId;

    /** 机构标识。 */
    private Long orgId;

    /** 操作人标识。 */
    private Long operatorId;
}
