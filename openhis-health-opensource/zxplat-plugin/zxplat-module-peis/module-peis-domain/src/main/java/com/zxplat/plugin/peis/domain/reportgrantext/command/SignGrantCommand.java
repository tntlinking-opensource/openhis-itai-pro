/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.reportgrantext.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 报告签收命令（DM-PEIS-005 REP-05）。
 */
@Getter
@Setter
public class SignGrantCommand {

    /** 总检报告主键。 */
    private Long diagnosticReportId;

    /** 签收人姓名（可选）。 */
    private String recipientName;

    /** 操作人标识。 */
    private Long operatorId;
}
